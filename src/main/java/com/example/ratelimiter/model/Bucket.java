package com.example.ratelimiter.model;

public record Bucket(double level, double lastTimestamp) {
}
