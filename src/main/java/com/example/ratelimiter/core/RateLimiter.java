package com.example.ratelimiter.core;

import java.util.HashMap;
import java.util.Map;

public record RateLimiter(int capacity, double leakRate, Map<String, Bucket> buckets) {

    public RateLimiter {
        buckets = Map.copyOf(buckets);
    }

    public RateLimiter withBucket(String userId, Bucket bucket) {
        Map<String, Bucket> next = new HashMap<>(buckets);
        next.put(userId, bucket);
        return new RateLimiter(capacity, leakRate, next);
    }
}
