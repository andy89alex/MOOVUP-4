package com.example.ratelimiter.model;

/**
 * @author Andi Hermanto
 * @since 2026-07-25
 */
public record Bucket(double level, double lastTimestamp) {
}
