package com.example.ratelimiter.service.impl;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.model.AllowResult;
import com.example.ratelimiter.model.Bucket;
import com.example.ratelimiter.model.RateLimiter;
import com.example.ratelimiter.service.RateLimiterOps;
import com.example.ratelimiter.service.RateLimiterService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Default rate limiter service. Holds the single global limiter state in an
 * {@link java.util.concurrent.atomic.AtomicReference} and swaps in the new
 * immutable state returned by the pure {@code RateLimiterOps} functions on
 * each request. Assumes single-threaded execution per the application spec.
 *
 * @author Andi Hermanto
 * @since 2026-07-25
 */
@Service
public class RateLimiterServiceImpl implements RateLimiterService {

    @Autowired
    private RateLimiterProperties props;

    private final AtomicReference<RateLimiter> state = new AtomicReference<>();

    @PostConstruct
    void init() {
        state.set(RateLimiterOps.createRateLimiter(props.getCapacity(), props.getLeakRate()));
    }

    @Override
    public AllowResult allowRequest(String userId, double timestamp) {
        AllowResult result = RateLimiterOps.allowRequest(state.get(), userId, timestamp);
        state.set(result.newState());
        return result;
    }

    @Override
    public Bucket getBucketState(String userId) {
        return RateLimiterOps.getBucketState(state.get(), userId);
    }
}
