package com.example.ratelimiter.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterOpsAllowTest {

    @Test
    void firstRequestFromNewUserIsAllowed() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0);
        AllowResult r = RateLimiterOps.allowRequest(rl, "user1", 0.0);

        assertTrue(r.allowed());
        assertEquals(new Bucket(1.0, 0.0), r.newState().buckets().get("user1"));
    }

    @Test
    void allowRequestDoesNotMutateInputLimiter() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0);
        RateLimiterOps.allowRequest(rl, "user1", 0.0);
        assertTrue(rl.buckets().isEmpty(), "input limiter must be unchanged");
    }

    @Test
    void burstBeyondCapacityIsRejected() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0);
        // 5 rapid requests at same timestamp fill the bucket to capacity
        boolean lastAllowed = true;
        for (int i = 0; i < 5; i++) {
            AllowResult r = RateLimiterOps.allowRequest(rl, "user1", 0.0);
            lastAllowed = r.allowed();
            rl = r.newState();
        }
        assertTrue(lastAllowed, "5th request fills to capacity and is allowed");

        AllowResult overflow = RateLimiterOps.allowRequest(rl, "user1", 0.0);
        assertFalse(overflow.allowed(), "6th request overflows and is rejected");
        // rejected: level stays at 5.0 (leaked=5, no +1), timestamp advances
        assertEquals(new Bucket(5.0, 0.0), overflow.newState().buckets().get("user1"));
    }

    @Test
    void bucketLeaksOverTimeAllowingLaterRequests() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0);
        // fill to capacity at t=0
        for (int i = 0; i < 5; i++) {
            rl = RateLimiterOps.allowRequest(rl, "user1", 0.0).newState();
        }
        // at t=0 it's full → reject
        assertFalse(RateLimiterOps.allowRequest(rl, "user1", 0.0).allowed());
        // after 1 second, 1 unit leaked → room for one → allow
        AllowResult afterLeak = RateLimiterOps.allowRequest(rl, "user1", 1.0);
        assertTrue(afterLeak.allowed());
        // leaked from 5 to 4, then +1 = 5, timestamp 1.0
        assertEquals(new Bucket(5.0, 1.0), afterLeak.newState().buckets().get("user1"));
    }

    @Test
    void usersHaveIndependentBuckets() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(1, 1.0);
        rl = RateLimiterOps.allowRequest(rl, "user1", 0.0).newState(); // user1 now full
        AllowResult u2 = RateLimiterOps.allowRequest(rl, "user2", 0.0);
        assertTrue(u2.allowed(), "user2 has its own empty bucket");
    }

    @Test
    void exactCapacityBoundaryIsAllowed() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(2, 1.0);
        rl = RateLimiterOps.allowRequest(rl, "u", 0.0).newState(); // level 1
        AllowResult r = RateLimiterOps.allowRequest(rl, "u", 0.0);  // leaked+1 == 2 == capacity
        assertTrue(r.allowed());
        assertEquals(new Bucket(2.0, 0.0), r.newState().buckets().get("u"));
    }
}
