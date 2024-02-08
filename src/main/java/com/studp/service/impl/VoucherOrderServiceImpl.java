package com.studp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.studp.dto.Result;
import com.studp.entity.SeckillVoucher;
import com.studp.entity.VoucherOrder;
import com.studp.mapper.SeckillVoucherMapper;
import com.studp.mapper.VoucherOrderMapper;
import com.studp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.studp.utils.RedisIdWorker;
import com.studp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;
    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    // lua脚本保证原子性
    private static final DefaultRedisScript<String> SECKILL_SCRIPT;  //

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(String.class);
    }

    /* 消息队列实现创建订单异步线程执行 */
    // 异步处理线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    // springboot 代理service bean
    IVoucherOrderService proxy;
    // 在类初始化之后提交（唯一一个）订单插入线程任务
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取消息队列中的订单信息
                    //   XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream()
                            .read(  Consumer.from("g1", "c1"),
                                    StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                                    StreamOffset.create("stream.orders", ReadOffset.lastConsumed()) );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有消息，继续下一次循环
                        continue;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单()
                    proxy.createVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream()
                            .acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    log.error("[VoucherHandler] 第一次处理失败，从pendingList中再次处理，异常日志：", e);
                    // 第一次处理失败，到未确认(ack)的 PendingList 中再次处理
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                    List<MapRecord<String, Object, Object>> list =
                            stringRedisTemplate.opsForStream()
                                    .read(  Consumer.from("g1", "c1"),
                                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                                            StreamOffset.create("stream.orders", ReadOffset.from("0")) );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有异常消息，结束循环
                        break;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.扣减库存、创建订单（持久化缓存到redis）
                    proxy.createVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream()
                            .acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    log.error("[VoucherHandler] 从pendingList中处理再次失败，异常日志：", e);
                    try {
                        Thread.sleep(20);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    /* 阻塞队列实现创建订单异步线程执行 */
//    // 阻塞队列
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
//    // 异步处理线程池
//    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
//    // springboot 代理service bean
//    IVoucherOrderService proxy;
//    // 在类初始化之后提交订单插入线程（唯一一个）任务，
//    // 因为当这个类初始化好了之后，随时都是有可能要执行的
//    @PostConstruct
//    private void init() {
//        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
//    }
//    // 用于线程池处理的任务
//    // 当初始化完毕后，就会去从阻塞队列中去拿信息
//    private class VoucherOrderHandler implements Runnable {
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    // 1.获取队列中的订单信息
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    // 2.创建订单
//                    log.info("[VoucherOrderHandler] 创建订单: {}", voucherOrder);
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                }
//            }
//        }
//        private void handleVoucherOrder(VoucherOrder voucherOrder) {
//            Long userId = voucherOrder.getUserId();
//            // 互斥锁
//            RLock redisLock = redissonClient.getLock("lock:order:" + userId);
//            boolean isLock = redisLock.tryLock();
//            if (!isLock) {
//                log.error("[VoucherOrder] 线程重复下单：id = {}", userId);
//                return;
//            }
//            try {
//                // 注意：由于是spring的事务是放在threadLocal中，此时的是多线程，事务会失效
//                proxy.createVoucherOrder(voucherOrder);
//            } finally {
//                // 释放锁
//                redisLock.unlock();
//            }
//        }
//    }

    /**
     * redis(lua脚本原子操作) + 阻塞队列||消息队列 实现：
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
        // 获取代理
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        /* 1.合理性检验、一人一券、扣减库存 */
        /* 2.发送创建订单消息数据到Stream，由异步线程读取处理 */
        String result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                List.of(voucherId.toString()),
                userId.toString(),
                orderId.toString());
        if (!"ok".equals(result)) {
            return Result.fail(result);
        }
        /* 2.检验通过，消息通知增加订单（多线程、阻塞队列实现） */
//        VoucherOrder voucherOrder = VoucherOrder.builder()
//                .id(orderId)
//                .userId(userId)
//                .voucherId(voucherId)
//                .build();
//          // 放入阻塞队列
//        orderTasks.add(voucherOrder);
        return Result.ok(orderId);   // 返回订单id
    }

    /**
     * 持久化到mysql：扣减库存 + 创建订单
     * @param voucherOrder id(orderId), voucherId, userId
     */
    public void createVoucherOrder(VoucherOrder voucherOrder) {
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
