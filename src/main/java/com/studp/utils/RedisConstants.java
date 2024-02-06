package com.studp.utils;

import com.studp.entity.Shop;

import java.util.HashMap;
import java.util.Map;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;
    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final Long CACHE_VOUCHER_TTL = 30L;

    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;
    public static final Long LOCK_VOUCHER_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";

    // key前缀
    private static final Map<Class<?>, String> prefixMap = new HashMap<>();
    // lockKey
    private static final Map<Class<?>, String> lockKeyMap = new HashMap<>();

    static {
        prefixMap.put(Shop.class, CACHE_SHOP_KEY);
        // ...
        lockKeyMap.put(Shop.class, LOCK_SHOP_KEY);
        // ...
    }

    public static String getPrefix(Class<?> type) {
        return prefixMap.get(type);
    }

    public static String getLockKey(Class<?> type) {
        return lockKeyMap.get(type);
    }
}
