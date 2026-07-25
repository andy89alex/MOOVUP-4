package com.example.ratelimiter.api;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.core.AllowResult;
import com.example.ratelimiter.core.Bucket;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.core.RateLimiterOps;
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
