package com.example.ratelimiter.dto;

/**
 * @author Andi Hermanto
 * @since 2026-07-25
 */
public record AllowResponseDto(boolean allowed, BucketStateDto bucket) {
}
