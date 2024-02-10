package com.studp.utils;

import com.studp.entity.Shop;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class RedisConstants {
    /* TTL+随机时间，防止缓存雪崩 */
    private final static Random random = new Random();
    public final static Long RANDOM_SECOND = (long) random.nextInt(60);
    public final static Long RANDOM_MINUTE = (long) random.nextInt(3);

    /* Login time unit: [minutes], Other time unit: [seconds] */
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final Long LOGIN_USER_TTL = 600L;
    public static final Long CACHE_NULL_TTL = 2L + random.nextInt(3);
    public static final Long CACHE_SHOP_TTL = 600L + random.nextInt(50);
    public static final Long CACHE_VOUCHER_TTL = 600L + + random.nextInt(50);;
    public static final Long LOCK_SHOP_TTL = 10L;
    public static final Long LOCK_VOUCHER_TTL = 10L;

    /* var | object keys */
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";


    /* tool keys (token、lock、redis data structure(set、zset、geo...)) */
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final String SECKILL_SET = "seckill:set:"; // + 优惠券id
    public static final String FOLLOW_SET = "follow:set:";   // + 用户id
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final String LOCK_VOUCHER_KEY = "lock:voucher:";

    /* 类对象 => key */
    private static final Map<Class<?>, String> prefixMap = new HashMap<>();
    private static final Map<Class<?>, String> lockKeyMap = new HashMap<>();
    static {
        prefixMap.put(Shop.class, CACHE_SHOP_KEY);
        // ...如有需要继续添加
        lockKeyMap.put(Shop.class, LOCK_SHOP_KEY);
        // ...如有需要继续添加
    }
    // 根据类对象得到对应key前缀
    public static String getPrefix(Class<?> type) {
        return prefixMap.get(type);
    }
    // 根据类对象得到对应lockKey前缀
    public static String getLockKey(Class<?> type) {
        return lockKeyMap.get(type);
    }
}
