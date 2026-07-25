package com.example.ratelimiter.core;

import java.util.Map;

public final class RateLimiterOps {

    private RateLimiterOps() {
    }

    public static RateLimiter createRateLimiter(int capacity, double leakRate) {
        return new RateLimiter(capacity, leakRate, Map.of());
    }

    public static Bucket getBucketState(RateLimiter rl, String userId) {
        return rl.buckets().get(userId);
    }
}
