package com.example.ratelimiter.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterModelTest {

    @Test
    void withBucketReturnsNewInstanceAndDoesNotMutateOriginal() {
        RateLimiter original = new RateLimiter(5, 1.0, Map.of());
        RateLimiter updated = original.withBucket("user1", new Bucket(2.0, 10.0));

        assertTrue(original.buckets().isEmpty(), "original must be unchanged");
        assertEquals(new Bucket(2.0, 10.0), updated.buckets().get("user1"));
        assertNotSame(original, updated);
    }

    @Test
    void bucketsMapIsUnmodifiable() {
        RateLimiter rl = new RateLimiter(5, 1.0, Map.of()).withBucket("u", new Bucket(1.0, 0.0));
        assertThrows(UnsupportedOperationException.class,
                () -> rl.buckets().put("x", new Bucket(0, 0)));
    }
}
