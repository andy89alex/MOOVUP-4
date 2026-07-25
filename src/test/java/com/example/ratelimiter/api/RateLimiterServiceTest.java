package com.example.ratelimiter.api;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.core.AllowResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private RateLimiterService newService(int capacity, double leakRate) {
        RateLimiterProperties props = new RateLimiterProperties();
        props.setCapacity(capacity);
        props.setLeakRate(leakRate);
        return new RateLimiterServiceImpl(props);
    }

    @Test
    void serviceSwapsStateBetweenCalls() {
        RateLimiterService service = newService(1, 1.0);
        AllowResult first = service.allowRequest("user1", 0.0);
        AllowResult second = service.allowRequest("user1", 0.0);

        assertTrue(first.allowed());
        assertFalse(second.allowed(), "state persisted across calls; bucket now full");
    }

    @Test
    void getBucketStateReflectsStoredState() {
        RateLimiterService service = newService(5, 1.0);
        assertNull(service.getBucketState("user1"));
        service.allowRequest("user1", 0.0);
        assertEquals(1.0, service.getBucketState("user1").level());
    }
}
