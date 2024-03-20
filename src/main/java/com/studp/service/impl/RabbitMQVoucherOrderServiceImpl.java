package com.studp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.studp.dto.Result;
import com.studp.dto.VoucherCreateMessage;
import com.studp.entity.VoucherOrder;
import com.studp.mapper.VoucherOrderMapper;
import com.studp.service.IVoucherOrderService;
import com.studp.utils.RedisIdWorker;
import com.studp.utils.UserHolder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

import static cn.hutool.http.ContentType.JSON;

@Service("rabbitMQ")
@Primary
public class RabbitMQVoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    RabbitTemplate rabbitTemplate;
    @Resource
    RedisIdWorker redisIdWorker;
    @Resource
    StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill2.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result saveSeckillVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long orderId = redisIdWorker.nextId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                List.of(voucherId.toString()),  // KEYs
                userId.toString(),
                orderId.toString() // ARGVs
        );
        int r = result.intValue();
        // 2.返回结果
        switch (r){
            case 1: return Result.fail("秒杀券不存在！");
            case 2: return Result.fail("库存不足！");
            case 3: return Result.fail("请勿重复下单！");
            default: {
                // 向MQ发送消息，异步创建订单入库
                rabbitTemplate.convertAndSend("direct.order", "create",
                        JSONUtil.toJsonStr(new VoucherCreateMessage(voucherId, userId, orderId)));
                return Result.ok(orderId);
            }
        }
    }

    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {

    }
}
