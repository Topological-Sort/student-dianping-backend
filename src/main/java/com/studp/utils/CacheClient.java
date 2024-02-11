package com.studp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.studp.utils.RedisConstants.CACHE_NULL_TTL;

/**
 * 两种能解决缓存穿透、缓存击穿的set和query缓存建立和查询方法
 * 缓存击穿分别用互斥锁、逻辑过期时间实现
 */
@Slf4j
@Component
public class CacheClient {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public void set(String key, Object value, Long ttl, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), ttl, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long ttl, TimeUnit unit){
        // 以redisData形式存储
        RedisData redisData = new RedisData();
        redisData.setData(value);  // 数据对象
        // 过期时间计算
        if(unit == TimeUnit.SECONDS)  // 设置逻辑过期时间
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(ttl));
        else if(unit == TimeUnit.MINUTES)
            redisData.setExpireTime(LocalDateTime.now().plusMinutes(ttl));
        else
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(ttl));
        // 存入带逻辑过期时间的对象
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithMutex(
            ID id, Class<R> type, Function<ID, R> dbFallback,
            Long ttl, TimeUnit unit){
        String key = RedisConstants.getPrefix(type) + id.toString();
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        /* 1.存在缓存，直接返回 */
        if(StrUtil.isNotBlank(jsonStr)){
            return JSONUtil.toBean(jsonStr, type);
        }
        if(jsonStr != null && jsonStr.isEmpty()){
            return null; // 防止缓存穿透
        }
        /* 2.不存在缓存，互斥查数据库，设置缓存 */
        R r = null;  // 查询结果
        String lockKey = RedisConstants.getLockKey(type);
        try {
            boolean success = tryLock(lockKey);
            // 2.1 获取锁失败
            if(!success){
                Thread.sleep(50);
                return queryWithMutex(id, type, dbFallback, ttl, unit);
            }
            // 2.2 获取锁成功
            r = dbFallback.apply(id);  // 查询数据库
            if(r == null)  // 防止缓存穿透
                set(key, "", CACHE_NULL_TTL, TimeUnit.SECONDS);
            else  // 建立缓存
                set(key, r, ttl, unit);
            return r; // 返回查询结果
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
    }

    public <R, ID> R queryWithLogicalExpire(
            ID id, Class<R> type, Function<ID, R> dbFallback,
            Long ttl, TimeUnit unit){
        String key = RedisConstants.getPrefix(type) + id.toString();
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if(jsonStr != null && jsonStr.isEmpty()){
            System.out.println("缓存穿透");
            return null; // 防止缓存穿透
        }
        /* 存在缓存且不为空字符串，判断缓存是否到期 */
        if(jsonStr != null){
            System.out.println("缓存不为空");
            /* 1.存在缓存且未过期，直接返回*/
            RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);
            R r = JSONUtil.toBean((JSONObject) redisData.getData(), type); //
            LocalDateTime expireTime = redisData.getExpireTime();
            // 未过期，直接返回
            if(expireTime.isAfter(LocalDateTime.now())){
                return r;
            }
            /* 2.缓存过期，互斥查数据库，设置缓存 */
            String lockKey = RedisConstants.getLockKey(type);
            try {
                boolean success = tryLock(lockKey);
                // 2.1 获取锁失败，直接返回旧数据
                if(!success)
                    return r;
                // 2.2 获取锁成功
                r = dbFallback.apply(id);  // 查询数据库
                if(r == null)    // 防止缓存穿透
                    set(key, "", CACHE_NULL_TTL, TimeUnit.SECONDS);
                else  // 建立缓存
                    setWithLogicalExpire(key, r, ttl, unit);
                return r; // 返回查询结果
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                unlock(lockKey);
            }
        }
        /* 不存在缓存，1.使用queryMutex互斥创建缓存并返回
        *           【2.创建一个已过期缓存，下次再查询时更新】*/
        setWithLogicalExpire(key, null, -1L, unit);
        return null;
    }

// Private --------------------------------------------------------
    private boolean tryLock(String key) {
        // 锁的ttl：10s（能防止死锁） || 直到解锁为止
        Boolean flag = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
// ----------------------------------------------------------------
}
