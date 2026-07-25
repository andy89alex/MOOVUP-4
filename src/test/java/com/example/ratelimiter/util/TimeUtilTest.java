package com.example.ratelimiter.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimeUtilTest {

    @Test
    void nowEpochSecondsIsCloseToSystemClock() {
        double expected = System.currentTimeMillis() / 1000.0;
        double actual = TimeUtil.nowEpochSeconds();
        assertTrue(Math.abs(actual - expected) < 2.0,
                "nowEpochSeconds should be within 2s of the system clock");
    }
}
