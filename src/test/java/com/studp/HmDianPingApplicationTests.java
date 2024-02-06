//package com.studp;
//
//import com.studp.utils.RedisIdWorker;
//import org.junit.jupiter.api.Test;
//import org.redisson.api.RLock;
//import org.redisson.api.RedissonClient;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.data.redis.core.StringRedisTemplate;
//
//import java.util.concurrent.*;
//
//@SpringBootTest
//class HmDianPingApplicationTests {
//    @Autowired
//    private StringRedisTemplate stringRedisTemplate;
//
//    @Autowired
//    private RedisIdWorker redisIdWorker;
//
//   //      private final CacheClient cacheClient;
//
//    @Test
//    public void testQueryWithLogicalExpire(){
//
//    }
//
//    @Test
//    void testIdWorker() throws InterruptedException {
//        CountDownLatch latch = new CountDownLatch(300);
//
//        Runnable task = () -> {
//            for (int i = 0; i < 10; i++) {
//                long id = redisIdWorker.nextId("order");
//                System.out.println("[Thread-" + Thread.currentThread() + "]" +" id = " + id);
//            }
//            latch.countDown();
//        };
//        long begin = System.currentTimeMillis();
//        Executors.newFixedThreadPool(2).submit(task);
//        latch.await();
//        long end = System.currentTimeMillis();
//        System.out.println("time = " + (end - begin));
//    }
//
//    @Autowired
//    private RedissonClient redissonClient;
//
//    @Test
//    void testRedisson() throws InterruptedException {
//        // 1.获取可重入锁（ReentrantLock）
//        RLock lock = redissonClient.getLock("anyLock");
//        // 2.尝试获取锁，参数分别是：获取锁的最大等待时间(期间会重试)，锁自动释放时间，时间单位
//        boolean success = lock.tryLock(1, 10, TimeUnit.SECONDS);
//        // 3.判断是否获取成功
//        if(success){
//            try{
//                System.out.println("执行业务");
//            } finally {
//                lock.unlock();
//            }
//        }
//    }
//
//}
