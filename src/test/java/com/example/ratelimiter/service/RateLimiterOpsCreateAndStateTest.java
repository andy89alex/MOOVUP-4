package com.example.ratelimiter.service;

import com.example.ratelimiter.model.Bucket;
import com.example.ratelimiter.model.RateLimiter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterOpsCreateAndStateTest {

    @Test
    void createRateLimiterStartsEmptyWithGivenConfig() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0);
        assertEquals(5, rl.capacity());
        assertEquals(1.0, rl.leakRate());
        assertTrue(rl.buckets().isEmpty());
    }

    @Test
    void getBucketStateReturnsNullForUnknownUser() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0);
        assertNull(RateLimiterOps.getBucketState(rl, "ghost"));
    }

    @Test
    void getBucketStateReturnsStoredBucket() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0)
                .withBucket("user1", new Bucket(3.0, 12.0));
        assertEquals(new Bucket(3.0, 12.0), RateLimiterOps.getBucketState(rl, "user1"));
    }
}
