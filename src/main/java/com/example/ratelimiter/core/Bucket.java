package com.example.ratelimiter.core;

public record Bucket(double level, double lastTimestamp) {
}
