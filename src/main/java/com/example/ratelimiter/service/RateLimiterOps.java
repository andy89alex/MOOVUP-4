package com.example.ratelimiter.service;

import com.example.ratelimiter.model.AllowResult;
import com.example.ratelimiter.model.Bucket;
import com.example.ratelimiter.model.RateLimiter;

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
        // Never let the stored clock rewind on an out-of-order (stale) timestamp;
        // otherwise a later request would over-leak and wrongly admit a burst.
        double stored = Math.max(timestamp, current.lastTimestamp());

        if (leaked + 1.0 <= rl.capacity()) {
            Bucket updated = new Bucket(leaked + 1.0, stored);
            return new AllowResult(true, rl.withBucket(userId, updated));
        }
        Bucket updated = new Bucket(leaked, stored);
        return new AllowResult(false, rl.withBucket(userId, updated));
    }
}
