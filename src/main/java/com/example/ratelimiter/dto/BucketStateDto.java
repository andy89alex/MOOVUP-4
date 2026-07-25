package com.example.ratelimiter.dto;

import com.example.ratelimiter.model.Bucket;

/**
 * @author Andi Hermanto
 * @since 2026-07-25
 */
public record BucketStateDto(double level, double lastTimestamp) {
    public static BucketStateDto from(Bucket bucket) {
        return new BucketStateDto(bucket.level(), bucket.lastTimestamp());
    }
}
