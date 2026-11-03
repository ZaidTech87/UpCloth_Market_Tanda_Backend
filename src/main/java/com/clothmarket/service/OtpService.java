package com.clothmarket.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * One-time passwords for password reset.
 *
 *  - generated with SecureRandom (the old code used Math.random())
 *  - expire after otp.ttl-seconds
 *  - at most otp.max-attempts wrong guesses, then the OTP is destroyed
 *  - a new OTP can only be requested every otp.resend-cooldown-seconds
 *  - compared in constant time
 *
 * State is held in memory, which is correct for a single backend instance
 * (current Render setup). With several instances move this to Redis/DB.
 */
@Service
public class OtpService {

    public enum Result { OK, INVALID, EXPIRED_OR_MISSING, TOO_MANY_ATTEMPTS }

    private static final class Entry {
        final String otp;
        final long expiresAtMs;
        final long issuedAtMs;
        int failedAttempts;

        Entry(String otp, long issuedAtMs, long expiresAtMs) {
            this.otp = otp;
            this.issuedAtMs = issuedAtMs;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, Entry> store = new ConcurrentHashMap<>();

    private final long ttlMs;
    private final int maxAttempts;
    private final long resendCooldownMs;
    private final LongSupplier clock;

    @Autowired
    public OtpService(@Value("${otp.ttl-seconds:300}") long ttlSeconds,
                      @Value("${otp.max-attempts:5}") int maxAttempts,
                      @Value("${otp.resend-cooldown-seconds:60}") long resendCooldownSeconds) {
        this(ttlSeconds, maxAttempts, resendCooldownSeconds, System::currentTimeMillis);
    }

    /** Test constructor with an injectable clock. */
    OtpService(long ttlSeconds, int maxAttempts, long resendCooldownSeconds, LongSupplier clock) {
        this.ttlMs = ttlSeconds * 1000L;
        this.maxAttempts = maxAttempts;
        this.resendCooldownMs = resendCooldownSeconds * 1000L;
        this.clock = clock;
    }

    /**
     * Seconds the caller must still wait before another OTP may be issued
     * for this mobile number (0 = may issue now).
     */
    public long secondsUntilResendAllowed(String mobile) {
        Entry existing = store.get(mobile);
        if (existing == null) {
            return 0;
        }
        long waitMs = existing.issuedAtMs + resendCooldownMs - clock.getAsLong();
        return waitMs <= 0 ? 0 : (waitMs + 999) / 1000;
    }

    /** Creates and stores a fresh 6-digit OTP, replacing any previous one. */
    public String issue(String mobile) {
        purgeExpired();
        long now = clock.getAsLong();
        String otp = String.format("%06d", random.nextInt(1_000_000));
        store.put(mobile, new Entry(otp, now, now + ttlMs));
        return otp;
    }

    /** Checks the OTP. A successful check consumes it (single use). */
    public Result verifyAndConsume(String mobile, String candidate) {
        Entry entry = store.get(mobile);
        if (entry == null) {
            return Result.EXPIRED_OR_MISSING;
        }
        synchronized (entry) {
            if (clock.getAsLong() > entry.expiresAtMs) {
                store.remove(mobile, entry);
                return Result.EXPIRED_OR_MISSING;
            }
            if (entry.failedAttempts >= maxAttempts) {
                store.remove(mobile, entry);
                return Result.TOO_MANY_ATTEMPTS;
            }
            boolean matches = candidate != null && MessageDigest.isEqual(
                    entry.otp.getBytes(StandardCharsets.UTF_8),
                    candidate.trim().getBytes(StandardCharsets.UTF_8));
            if (matches) {
                store.remove(mobile, entry);
                return Result.OK;
            }
            entry.failedAttempts++;
            if (entry.failedAttempts >= maxAttempts) {
                store.remove(mobile, entry);
                return Result.TOO_MANY_ATTEMPTS;
            }
            return Result.INVALID;
        }
    }

    /** Drops an OTP, e.g. when the SMS could not be sent. */
    public void discard(String mobile) {
        store.remove(mobile);
    }

    private void purgeExpired() {
        long now = clock.getAsLong();
        store.entrySet().removeIf(e -> now > e.getValue().expiresAtMs);
    }
}
