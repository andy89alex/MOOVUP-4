package com.example.ratelimiter.service;

import com.example.ratelimiter.model.AllowResult;
import com.example.ratelimiter.model.Bucket;

public interface RateLimiterService {
    AllowResult allowRequest(String userId, double timestamp);
    Bucket getBucketState(String userId);
}
