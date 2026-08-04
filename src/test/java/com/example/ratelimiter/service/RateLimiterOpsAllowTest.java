package com.example.ratelimiter.service;

import com.example.ratelimiter.model.AllowResult;
import com.example.ratelimiter.model.Bucket;
import com.example.ratelimiter.model.RateLimiter;
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
    void basicFunctionalitySequenceFromSpec() {
        // create_rate_limiter(capacity=5, leak_rate=1.0)
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0);

        // [allowed1, limiter] = allow_request(limiter, "user1", timestamp=0)
        AllowResult first = RateLimiterOps.allowRequest(rl, "user1", 0.0);
        assertTrue(first.allowed());
        assertEquals(new Bucket(1.0, 0.0), first.newState().buckets().get("user1"));

        // [allowed2, limiter] = allow_request(limiter, "user1", timestamp=1)
        // One second at leakRate 1.0 drains the single unit, so the level is
        // back to 1.0 rather than accumulating to 2.0.
        AllowResult second = RateLimiterOps.allowRequest(first.newState(), "user1", 1.0);
        assertTrue(second.allowed());
        assertEquals(new Bucket(1.0, 1.0), second.newState().buckets().get("user1"));

        // Config carries through the returned states untouched.
        assertEquals(5, second.newState().capacity());
        assertEquals(1.0, second.newState().leakRate());
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
    void usersTrackIndependentLevelsAndTimestamps() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0);
        // user1 makes two requests at t=0 → level 2.0 (same timestamp, no leaking).
        rl = RateLimiterOps.allowRequest(rl, "user1", 0.0).newState();
        rl = RateLimiterOps.allowRequest(rl, "user1", 0.0).newState();
        // user2's first request arrives much later at t=5.
        rl = RateLimiterOps.allowRequest(rl, "user2", 5.0).newState();

        // Each user keeps its own level and its own clock; user2's later
        // timestamp must not leak user1's bucket.
        assertEquals(new Bucket(2.0, 0.0), rl.buckets().get("user1"));
        assertEquals(new Bucket(1.0, 5.0), rl.buckets().get("user2"));
        assertEquals(2, rl.buckets().size());
    }

    @Test
    void exactCapacityBoundaryIsAllowed() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(2, 1.0);
        rl = RateLimiterOps.allowRequest(rl, "u", 0.0).newState(); // level 1
        AllowResult r = RateLimiterOps.allowRequest(rl, "u", 0.0);  // leaked+1 == 2 == capacity
        assertTrue(r.allowed());
        assertEquals(new Bucket(2.0, 0.0), r.newState().buckets().get("u"));
    }

    @Test
    void outOfOrderTimestampDoesNotRewindBucketClock() {
        // Fill to capacity at t=100.
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0);
        for (int i = 0; i < 5; i++) {
            rl = RateLimiterOps.allowRequest(rl, "user1", 100.0).newState();
        }
        // A stale request at t=50 must not roll the stored clock backward.
        RateLimiter afterStale = RateLimiterOps.allowRequest(rl, "user1", 50.0).newState();
        assertEquals(100.0, afterStale.buckets().get("user1").lastTimestamp(),
                "stored timestamp must not rewind below the last observed time");

        // Because the clock did not rewind, a request at t=101 leaks only 1s (not 51s):
        // exactly one slot frees, so one request is admitted and the bucket is full again.
        RateLimiter afterT101 = RateLimiterOps.allowRequest(afterStale, "user1", 101.0).newState();
        assertEquals(new Bucket(5.0, 101.0), afterT101.buckets().get("user1"));
        // A second request at t=101 must be rejected — the stale timestamp granted no burst.
        assertFalse(RateLimiterOps.allowRequest(afterT101, "user1", 101.0).allowed(),
                "out-of-order request must not enable a burst on the next request");
    }
}
