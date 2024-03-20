package com.studp.listener;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.studp.dto.VoucherCreateMessage;
import com.studp.entity.SeckillVoucher;
import com.studp.entity.VoucherOrder;
import com.studp.mapper.SeckillVoucherMapper;
import com.studp.mapper.VoucherOrderMapper;
import com.studp.service.ISeckillVoucherService;
import com.studp.service.IVoucherOrderService;
import jodd.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class VoucherOrderCreateListener {

    @Autowired
    VoucherOrderMapper voucherOrderMapper;
    @Autowired
    ISeckillVoucherService seckillVoucherService;
    @Autowired
    SeckillVoucherMapper seckillVoucherMapper;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "direct.voucher.order.queue"),
            exchange = @Exchange(name = "direct.order", type = ExchangeTypes.DIRECT),
            key = "create"  // key为create时，路由到该队列，传到该监听器
    ))
    public void VoucherOrderCreate(String message) {
        VoucherCreateMessage voucherCreateMessage = JSON.parseObject(message, VoucherCreateMessage.class);
        Long userId = voucherCreateMessage.getUserId();
        Long voucherId = voucherCreateMessage.getVoucherId();
        Long orderId = voucherCreateMessage.getOrderId();
        VoucherOrder voucherOrder = VoucherOrder.builder()
                .id(orderId) .voucherId(voucherId) .userId(userId) .build();
        /* 1.扣减库存（update） */
        SeckillVoucher voucher = BeanUtil.copyProperties(voucherOrder, SeckillVoucher.class);
        int lines = seckillVoucherMapper.update(voucher,  // lines: 操作影响的行数
                new LambdaUpdateWrapper<SeckillVoucher>()
                        .setSql("stock = stock - 1")
                        .eq(SeckillVoucher::getVoucherId, voucher.getVoucherId())
                        .gt(SeckillVoucher::getStock, 0));  // 库存需>0才能执行成功
        // .ge(Voucher::getStock, voucher.getStock()));
        // 乐观锁，即将更新时，其值和更新前的期望值相等。
        // 否则说明执行期间有其他进程对该条记录进行了写操作
        // 高并发时会造成大量的失败操作（100个线程只有1个会成功）
        if (lines == 0) { // 库存不足
            return;
        }
        /* 2.创建秒杀券订单（insert） */
        voucherOrderMapper.insert(voucherOrder);
    }
}
