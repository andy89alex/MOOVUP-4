package com.example.ratelimiter.service.impl;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.model.AllowResult;
import com.example.ratelimiter.model.Bucket;
import com.example.ratelimiter.model.RateLimiter;
import com.example.ratelimiter.service.RateLimiterOps;
import com.example.ratelimiter.service.RateLimiterService;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class RateLimiterServiceImpl implements RateLimiterService {

    private final AtomicReference<RateLimiter> state;

    public RateLimiterServiceImpl(RateLimiterProperties props) {
        this.state = new AtomicReference<>(
                RateLimiterOps.createRateLimiter(props.getCapacity(), props.getLeakRate()));
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
