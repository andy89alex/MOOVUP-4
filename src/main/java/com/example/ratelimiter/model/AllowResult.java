package com.example.ratelimiter.model;

public record AllowResult(boolean allowed, RateLimiter newState) {
}
