package com.example.ratelimiter.util;

/**
 * Utility for time values used by the rate limiter.
 * Provides the current server time as Unix epoch seconds (floating-point),
 * used as the default request timestamp when a client does not supply one.
 *
 * @author Andi Hermanto
 * @since 2026-07-25
 */
public final class TimeUtil {

    private TimeUtil() {
    }

    /**
     * Returns the current server time as Unix epoch seconds (floating-point).
     *
     * @return current time in seconds since the Unix epoch
     */
    public static double nowEpochSeconds() {
        return System.currentTimeMillis() / 1000.0;
    }
}
