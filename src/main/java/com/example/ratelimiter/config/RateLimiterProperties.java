package com.example.ratelimiter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Andi Hermanto
 * @since 2026-07-25
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ratelimiter")
public class RateLimiterProperties {
    private int capacity = 5;
    private double leakRate = 1.0;
}
