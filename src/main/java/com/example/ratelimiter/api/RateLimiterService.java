package com.example.ratelimiter.api;

import com.example.ratelimiter.core.AllowResult;
import com.example.ratelimiter.core.Bucket;

public interface RateLimiterService {
    AllowResult allowRequest(String userId, double timestamp);
    Bucket getBucketState(String userId);
}
