package com.clothmarket.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    @Test
    void locksAfterMaxFailuresAndUnlocksAfterTimeout() {
        AtomicLong t = new AtomicLong(0);
        LoginAttemptService s = new LoginAttemptService(3, 900_000, t::get);
        s.recordFailure("m");
        s.recordFailure("m");
        assertFalse(s.isLocked("m"));
        s.recordFailure("m");
        assertTrue(s.isLocked("m"));
        t.addAndGet(900_001);
        assertFalse(s.isLocked("m"));
    }

    @Test
    void successResetsTheCounter() {
        AtomicLong t = new AtomicLong(0);
        LoginAttemptService s = new LoginAttemptService(3, 900_000, t::get);
        s.recordFailure("x");
        s.recordFailure("x");
        s.recordSuccess("x");
        s.recordFailure("x");
        s.recordFailure("x");
        assertFalse(s.isLocked("x"));
    }
}
