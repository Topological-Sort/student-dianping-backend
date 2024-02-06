package com.studp.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Data
@AllArgsConstructor
public class SimpleRedisLock implements ILock{
    private final String KEY_PREFIX = "lock:";

    private String name;  // 锁名
    private StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;  //
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String ThreadId = String.valueOf(Thread.currentThread().getId());
        // 在redis中设置锁记录: [KEY, ThreadId]
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, ThreadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    // lua脚本保证原子操作(读取锁id 判断是否是当前线程的id(锁)，释放锁)
    @Override
    public void unlock() {
        String ThreadId = String.valueOf(Thread.currentThread().getId());
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ThreadId);
    }

//    // 普通方式，需要执行两个redis操作，redis的事务不能保证一致性（最终一致性）
//    @Override
//    public void unlock() {
//        String ThreadId = String.valueOf(Thread.currentThread().getId());
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if(ThreadId.equals(id)) {  // 判断该锁是由这个线程获得的，防止误删其他线程的同名锁
//            stringRedisTemplate.delete(KEY_PREFIX + name); // 由这个线程释放
//        }
//    }
}
