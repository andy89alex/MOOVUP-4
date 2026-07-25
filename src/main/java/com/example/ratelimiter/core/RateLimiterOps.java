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

    public static AllowResult allowRequest(RateLimiter rl, String userId, double timestamp) {
        Bucket current = rl.buckets().getOrDefault(userId, new Bucket(0.0, timestamp));
        double elapsed = Math.max(0.0, timestamp - current.lastTimestamp());
        double leaked = Math.max(0.0, current.level() - rl.leakRate() * elapsed);

        if (leaked + 1.0 <= rl.capacity()) {
            Bucket updated = new Bucket(leaked + 1.0, timestamp);
            return new AllowResult(true, rl.withBucket(userId, updated));
        }
        Bucket updated = new Bucket(leaked, timestamp);
        return new AllowResult(false, rl.withBucket(userId, updated));
    }
}
