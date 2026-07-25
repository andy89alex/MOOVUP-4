package com.example.ratelimiter.model;

/**
 * @author Andi Hermanto
 * @since 2026-07-25
 */
public record AllowResult(boolean allowed, RateLimiter newState) {
}
