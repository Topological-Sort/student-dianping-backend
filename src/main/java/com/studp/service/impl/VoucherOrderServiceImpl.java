package com.studp.service.impl;

import cn.hutool.db.Db;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.studp.dto.Result;
import com.studp.entity.SeckillVoucher;
import com.studp.entity.Voucher;
import com.studp.entity.VoucherOrder;
import com.studp.mapper.SeckillVoucherMapper;
import com.studp.mapper.VoucherMapper;
import com.studp.mapper.VoucherOrderMapper;
import com.studp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.studp.utils.RedisIdWorker;
import com.studp.utils.SimpleRedisLock;
import com.studp.utils.UserHolder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final SeckillVoucherMapper seckillVoucherMapper;

    private final VoucherOrderMapper voucherOrderMapper;

    private final RedisIdWorker redisIdWorker;

    private final RedissonClient redissonClient;

    private final StringRedisTemplate stringRedisTemplate;
    // lua脚本保证原子性
    private static final DefaultRedisScript<String> SECKILL_SCRIPT;  //
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(String.class);
    }

    // 阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    // 异步处理线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    // 代理
    IVoucherOrderService proxy;

    //在类初始化之后执行，因为当这个类初始化好了之后，随时都是有可能要执行的
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 用于线程池处理的任务
    // 当初始化完毕后，就会去从对列中去拿信息
    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    log.info("[VoucherOrderHandler] 创建订单: {}", voucherOrder);
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
        private void handleVoucherOrder(VoucherOrder voucherOrder) {
            Long userId = voucherOrder.getUserId();
            // 互斥锁
            RLock redisLock = redissonClient.getLock("lock:order:" + userId);
            boolean isLock = redisLock.tryLock();
            if (!isLock) {
                log.error("[VoucherOrder] 线程重复下单：id = {}", userId);
                return;
            }
            try {
                // 注意：由于是spring的事务是放在threadLocal中，此时的是多线程，事务会失效
                proxy.save(voucherOrder);
            } finally {
                // 释放锁
                redisLock.unlock();
            }
        }
    }

    /**
     * redis + 阻塞队列 实现(原子操作)：
     * (1) 秒杀券合理性 [String]stock - insert  秒杀券存在、库存。
     * (2) 一人一券 [Set]userId - insert  voucher的set中是否已经存在该用户id
     * (3) 扣减库存 [String]stock - query  redis中库存-1，同步更新到mysql
     * 消息队列、异步线程：
     * [Stream]: userId, orderId, voucherId
     * (4) 插入新订单 [insert] 根据voucherId, userId, 插入mysql【更新[Set]】
     */
    @Override
    @Transactional
    public Result<Long> saveSeckillVoucherOrder(Long voucherId) throws InterruptedException {
        Long userId = UserHolder.getUser().getId();
        Long orderId = redisIdWorker.nextId("order");
        /* 1.合理性检验、一人一券、扣减库存 */
        String result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                List.of(voucherId.toString()),
                userId.toString(),
                orderId.toString());
        if (!"ok".equals(result)) {
            return Result.fail(result);
        }
        // TODO: 将库存数据从 redis 持久化到 mysql
        /* 2.通过，消息通知增加订单 */
        VoucherOrder voucherOrder = VoucherOrder.builder()
                .id(orderId)
                .userId(userId)
                .voucherId(voucherId)
                .build();
        // 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 放入阻塞队列
        orderTasks.add(voucherOrder);
        // 返回订单id
        return Result.ok(orderId);
    }

    /**
     * redis 分布式锁实现
     */
//    @Transactional
//    @Override
//    public Result<Long> saveSeckillVoucherOrder(Long voucherId) throws InterruptedException {
//        /* 1.秒杀券合理性检验（select） */
//        // 1.1 查询秒杀券是否存在
//        SeckillVoucher voucher = seckillVoucherMapper.selectOne(
//                new LambdaQueryWrapper<SeckillVoucher>()
//                        .eq(SeckillVoucher::getVoucherId, voucherId));
//        if(voucher == null){
//            return Result.fail("秒杀券不存在！");
//        }
//        // 1.2 判断秒杀是否开始
//        if(LocalDateTime.now().isBefore(voucher.getBeginTime())){
//            return Result.fail("秒杀活动尚未开始！");
//        }
//        // 1.3 判断秒杀是否已结束
//        if(LocalDateTime.now().isAfter(voucher.getEndTime())){
//            return Result.fail("秒杀活动已结束！");
//        }
//        // 1.4 判断库存是否充足
//        if(voucher.getStock() <= 0){
//            return Result.fail("秒杀券已被抢光！");
//        }
//        /* 2.分布式锁原子操作，分布式锁代替悲观锁(悲观锁只能解决非集群环境下的并发异常)
//            (1) 一人一单（select）
//            (2) 扣减库存（update）
//            (3) 创建订单（insert） */
//        // 分布式锁
//        Long userId = UserHolder.getUser().getId();
//        // redisson库函数
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean success = lock.tryLock();
//        //log.info("[VoucherOrder] 抢分布式锁, userId = {}, success = {}", userId, success);
//        if(!success){
//            return Result.fail("请勿重复下单！");
//        }
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
////        boolean success = lock.tryLock(10);
////        log.info("[VoucherOrder] 抢分布式锁, userId = {}, success = {}", userId, success);
////        if(!success){  // 加锁失败，已经有线程获得锁
////            return Result.fail("请勿重复下单！");
////        }
//        try {
//            /* 1. 一人一单（select）*/
//            // 创建订单、扣减库存前先判断该用户是否已经购买过 */
//            VoucherOrder order = voucherOrderMapper.selectOne(
//                    new LambdaQueryWrapper<VoucherOrder>()
//                            .eq(VoucherOrder::getUserId, UserHolder.getUser().getId()));
//            if(order != null){  // 已经有一个秒杀券订单
//                return Result.fail("每人只能抢一张券！");
//            }
//            /* 2.扣减库存（update） */
//            int lines = seckillVoucherMapper.update(voucher,  // lines: 操作后受影响的行数
//                    new LambdaUpdateWrapper<SeckillVoucher>()
//                            .setSql("stock = stock - 1")
//                            .eq(SeckillVoucher::getVoucherId, voucher.getVoucherId())
//                            .gt(SeckillVoucher::getStock, 0));  // 库存需>0才能执行成功
////                        // 乐观锁，即将更新时，其值和更新前的期望值相等。
////                        // 否则说明执行期间有其他进程对该条记录进行了写操作
////                        // 高并发时会造成大量的失败操作（100个线程只有1个会成功）
////                        .ge(Voucher::getStock, voucher.getStock()));
//            boolean res = (lines >= 1);
//            if (!res) {
//                return Result.fail("库存不足！");
//            }
//            /* 3.创建秒杀券订单（insert） */
//            Long orderId = redisIdWorker.nextId("order"); //生成分布式订单id
//            VoucherOrder voucherOrder = VoucherOrder.builder()
//                    .id(orderId)  // 订单id
//                    .userId(UserHolder.getUser().getId())  // 用户id
//                    .voucherId(voucherId)  // 代金券id
//                    .build();
//            voucherOrderMapper.insert(voucherOrder);
//            return Result.ok(orderId);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        } finally {
//            lock.unlock();
//        }
//    }

    /**
     * 悲观锁实现
     */
//    @Transactional
//    @Override
//    public Result<Long> saveSeckillVoucherOrder(Long voucherId) throws InterruptedException {
//        /* 1.秒杀券合理性检验（select） */
//        // 1.1 查询秒杀券是否存在
//        SeckillVoucher voucher = seckillVoucherMapper.selectOne(
//                new LambdaQueryWrapper<SeckillVoucher>()
//                        .eq(SeckillVoucher::getVoucherId, voucherId));
//        if (voucher == null) {
//            return Result.fail("秒杀券不存在！");
//        }
//        // 1.2 判断秒杀是否开始
//        if (LocalDateTime.now().isBefore(voucher.getBeginTime())) {
//            return Result.fail("秒杀活动尚未开始！");
//        }
//        // 1.3 判断秒杀是否已结束
//        if (LocalDateTime.now().isAfter(voucher.getEndTime())) {
//            return Result.fail("秒杀活动已结束！");
//        }
//        // 1.4 判断库存是否充足
//        if (voucher.getStock() <= 0) {
//            return Result.fail("秒杀券已被抢光！");
//        }
//        /* 2.悲观锁(悲观锁只能解决非集群环境下的并发异常)
//             (1) 一人一单（select），
//             (2) 扣减库存（update）
//             (3) 创建秒杀券订单（insert）*/
//        Long userId = UserHolder.getUser().getId();
//        try{
//            synchronized (userId.toString().intern()){  // intern()：从常量池取值，确保是上面拿到的这个userId，否则是new的结果
////                // 获取Spring事务代理对象（减小锁粒度）
////                IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////                return proxy.createVoucherOrder(userId, voucherId);
//                return createVoucherOrder(userId, voucherId);
//            }
//        } catch (Exception e) { //
//            throw new RuntimeException(e);
//        }
//    }
//
//
//    // 原子操作，防止出现并发线程都通过 if 判断，重复创建订单的情况
//    public Result<Long> createVoucherOrder(Long userId, Long voucherId) {
//        synchronized (userId.toString().intern()){
//            /* 1. 一人一单（select）*/ 
//                // 创建订单、扣减库存前先判断该用户是否已经购买过 */
//            VoucherOrder order = voucherOrderMapper.selectOne(
//                    new LambdaQueryWrapper<VoucherOrder>()
//                            .eq(VoucherOrder::getUserId, UserHolder.getUser().getId()));
//            if(order != null){  // 已经有一个秒杀券订单
//                return Result.fail("每人只能抢一张券！");
//            }
//            /* 2.扣减库存（update） */
//            int lines = seckillVoucherMapper.update(voucher,  // lines: 操作后受影响的行数
//                    new LambdaUpdateWrapper<SeckillVoucher>()
//                            .setSql("stock = stock - 1")
//                            .eq(SeckillVoucher::getVoucherId, voucher.getVoucherId())
//                            .gt(SeckillVoucher::getStock, 0));  // 库存需>0才能执行成功
////                        // 乐观锁，即将更新时，其值和更新前的期望值相等。
////                        // 否则说明执行期间有其他进程对该条记录进行了写操作
////                        // 高并发时会造成大量的失败操作（100个线程只有1个会成功）
////                        .ge(Voucher::getStock, voucher.getStock()));
//            boolean res = (lines >= 1);
//            if (!res) {
//                return Result.fail("库存不足！");
//            }
//            /* 3.创建秒杀券订单（insert） */
//            Long orderId = redisIdWorker.nextId("order"); //生成分布式订单id
//            VoucherOrder voucherOrder = VoucherOrder.builder()
//                    .id(orderId)  // 订单id
//                    .userId(UserHolder.getUser().getId())  // 用户id
//                    .voucherId(voucherId)  // 代金券id
//                    .build();
//            voucherOrderMapper.insert(voucherOrder);
//            return Result.ok(orderId);
//        }
//    }

}
