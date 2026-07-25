package com.example.ratelimiter.api.dto;

import com.example.ratelimiter.core.Bucket;

public record BucketStateDto(double level, double lastTimestamp) {
    public static BucketStateDto from(Bucket bucket) {
        return new BucketStateDto(bucket.level(), bucket.lastTimestamp());
    }
}
