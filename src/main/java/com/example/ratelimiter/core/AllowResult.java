package com.example.ratelimiter.core;

public record AllowResult(boolean allowed, RateLimiter newState) {
}
