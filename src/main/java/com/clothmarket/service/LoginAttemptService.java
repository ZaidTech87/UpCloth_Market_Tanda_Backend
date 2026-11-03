package com.clothmarket.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * Per-account brute-force protection. The IP rate limiter alone does not stop
 * a distributed attack against ONE account; this locks a mobile number for a
 * while after too many consecutive wrong passwords.
 *
 * In-memory (fine for one instance; move to Redis/DB for several).
 */
@Service
public class LoginAttemptService {

    private static final class State {
        int failures;
        long lockedUntilMs;
    }

    private final ConcurrentMap<String, State> states = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final long lockoutMs;
    private final LongSupplier clock;

    @Autowired
    public LoginAttemptService(@Value("${auth.max-failed-logins:5}") int maxFailures,
                               @Value("${auth.lockout-minutes:15}") long lockoutMinutes) {
        this(maxFailures, lockoutMinutes * 60_000L, System::currentTimeMillis);
    }

    /** Test constructor with an injectable clock. */
    LoginAttemptService(int maxFailures, long lockoutMs, LongSupplier clock) {
        this.maxFailures = maxFailures;
        this.lockoutMs = lockoutMs;
        this.clock = clock;
    }

    public boolean isLocked(String mobile) {
        State s = states.get(mobile);
        if (s == null) {
            return false;
        }
        synchronized (s) {
            if (s.lockedUntilMs > clock.getAsLong()) {
                return true;
            }
            if (s.lockedUntilMs != 0) { // lock has expired -> start fresh
                states.remove(mobile, s);
            }
            return false;
        }
    }

    public void recordFailure(String mobile) {
        State s = states.computeIfAbsent(mobile, k -> new State());
        synchronized (s) {
            s.failures++;
            if (s.failures >= maxFailures) {
                s.lockedUntilMs = clock.getAsLong() + lockoutMs;
                s.failures = 0;
            }
        }
    }

    public void recordSuccess(String mobile) {
        states.remove(mobile);
    }
}
