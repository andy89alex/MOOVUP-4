package com.example.ratelimiter.dto;

public record AllowResponseDto(boolean allowed, BucketStateDto bucket) {
}
