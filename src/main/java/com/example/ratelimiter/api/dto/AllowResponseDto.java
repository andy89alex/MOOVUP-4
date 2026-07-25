package com.example.ratelimiter.api.dto;

public record AllowResponseDto(boolean allowed, BucketStateDto bucket) {
}
