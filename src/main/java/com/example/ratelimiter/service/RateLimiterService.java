package com.example.ratelimiter.service;

import com.example.ratelimiter.model.AllowResult;
import com.example.ratelimiter.model.Bucket;

/**
 * Business-logic contract for the leaky-bucket rate limiter.
 * Decides whether a user's request is allowed and exposes the current
 * bucket state for a user. Implementations hold the single global limiter
 * state and delegate the algorithm to the pure functional core.
 *
 * @author Andi Hermanto
 * @since 2026-07-25
 */
public interface RateLimiterService {
    AllowResult allowRequest(String userId, double timestamp);
    Bucket getBucketState(String userId);
}
