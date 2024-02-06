package com.studp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.studp.dto.Result;
import com.studp.entity.Voucher;
import com.studp.entity.VoucherOrder;
import com.studp.mapper.VoucherMapper;
import com.studp.mapper.VoucherOrderMapper;
import com.studp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.studp.utils.RedisIdWorker;
import com.studp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final VoucherMapper voucherMapper;

    private final VoucherOrderMapper voucherOrderMapper;

    private final RedisIdWorker redisIdWorker;

//    private final StringRedisTemplate stringRedisTemplate;

    private final RedissonClient redissonClient;

    @Transactional
    @Override
    public Result<Long> saveSeckillVoucher(Long voucherId) {
        /* 1.秒杀券合理性检验（select） */
        // 1.1 查询秒杀券是否存在
        Voucher voucher = voucherMapper.selectById(voucherId);
        if(voucher == null){
            return Result.fail("秒杀券不存在！");
        }
        // 1.2 判断秒杀是否开始
        if(LocalDateTime.now().isBefore(voucher.getBeginTime())){
            return Result.fail("秒杀活动尚未开始！");
        }
        // 1.3 判断秒杀是否已结束
        if(LocalDateTime.now().isAfter(voucher.getEndTime())){
            return Result.fail("秒杀活动已结束！");
        }
        // 1.4 判断库存是否充足
        if(voucher.getStock() <= 0){
            return Result.fail("秒杀券已被抢光！");
        }

        /* 2.扣减库存（update） */
        int lines = voucherMapper.update(voucher,  // lines: 操作后受影响的行数
                new LambdaUpdateWrapper<Voucher>()
                        .setSql("stock = stock - 1")
                        .eq(Voucher::getId, voucher.getId())
                        .gt(Voucher::getStock, 0));  // 库存需>0才能执行成功
//                        // 乐观锁，即将更新时，其值和更新前的期望值相等。
//                        // 否则说明执行期间有其他进程对该条记录进行了写操作
//                        // 高并发时会造成大量的失败操作（100个线程只有1个会成功）
//                        .ge(Voucher::getStock, voucher.getStock()));
        boolean res = (lines >= 1);
        if(!res){
            return Result.fail("库存不足！");
        }

        /* 3.创建订单（insert）并返回结果 */
        Long userId = UserHolder.getUser().getId();
        // 3.1 分布式锁
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean success = lock.tryLock();
        if(!success){
            return Result.fail("请勿重复下单！");
        }
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        boolean success = lock.tryLock(1200);
//        if(!success){  // 加锁失败，已经有线程获得锁
//            return Result.fail("请勿重复下单！");
//        }
        // 3.2 悲观锁 synchronized
        try{
            synchronized (userId.toString().intern()){  // intern()：从常量池取值，确保是上面拿到的这个userId，否则是new的结果
                // 获取Spring事务代理对象（减小锁粒度）
                IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
                return proxy.createVoucherOrder(voucherId);
            }
        } finally {
            lock.unlock(); // 无论是否成功，分布式锁都在最后解锁
        }
    }

    // 原子操作，防止出现并发线程都通过 if 判断，重复创建订单的情况
    public Result<Long> createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()){
            // 1. 一人一单，创建订单前先判断，该用户是否已经购买过
            VoucherOrder order = voucherOrderMapper.selectOne(new LambdaQueryWrapper<VoucherOrder>()
                    .eq(VoucherOrder::getUserId, UserHolder.getUser().getId()));
            if(order != null){  // 已经有一个秒杀券订单
                return Result.fail("每人只能抢一张券！");
            }
            // 2. 创建秒杀券订单
            Long orderId = redisIdWorker.nextId("order"); //生成分布式订单id
            VoucherOrder voucherOrder = VoucherOrder.builder()
                    .id(orderId)  // 订单id
                    .userId(UserHolder.getUser().getId())  // 用户id
                    .voucherId(voucherId)  // 代金券id
                    .build();
            voucherOrderMapper.insert(voucherOrder);
            return Result.ok(orderId);
        }
    }
}
