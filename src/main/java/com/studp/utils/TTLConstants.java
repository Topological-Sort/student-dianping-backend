package com.studp.utils;


import java.util.Random;

public class TTLConstants {

    private final static Random random = new Random();
    // second
    public final static Long TTL = 36000L + random.nextInt(60);
    // second
    public final static Long CACHE_EMPTY_TTL = 60L + random.nextInt(10);
    // second
    public final static Long RANDOM_SECOND = (long) random.nextInt(60);
    // minute
    public final static Long RANDOM_MINUTE = (long) random.nextInt(3);
}
