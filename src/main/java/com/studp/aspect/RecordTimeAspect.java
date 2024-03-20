package com.studp.aspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Aspect
@Component
public class RecordTimeAspect {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Pointcut("execution(* com.studp.*.*.*(..)) && @annotation(com.studp.annotation.RecordTime)")
    public void recordTimePointcut(){}

    @Around("recordTimePointcut()")
    public Object recordTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long end = System.currentTimeMillis();
        // 将耗时累加到redis的记录中，记录将订单持久化到mysql的耗时
        String key = "createOrder:time";
        stringRedisTemplate.opsForHash()
                .increment(key, "count", 1);
        stringRedisTemplate.opsForHash()
                .increment(key, "cost", end - start);
        return result;
    }
}
