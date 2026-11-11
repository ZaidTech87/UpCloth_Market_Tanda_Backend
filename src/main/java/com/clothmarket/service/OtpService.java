package com.clothmarket.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * One-time passwords sent to an email address, for signup and password reset.
 *
 *  - generated with SecureRandom
 *  - bound to a PURPOSE (SIGNUP or PASSWORD_RESET) and an email address, so a code issued
 *    for one purpose can never be used for the other, or for another address
 *  - only a keyed hash (HMAC-SHA256) is kept in memory, never the code itself
 *  - expire after otp.ttl-seconds
 *  - at most otp.max-attempts wrong guesses, then the OTP is destroyed
 *  - single use: a successful check consumes it
 *  - a new OTP can only be requested every otp.resend-cooldown-seconds; the cooldown
 *    survives a consumed or exhausted OTP, so it cannot be used to flood an inbox
 *  - hashes are compared in constant time
 *
 * State is held in memory, which is correct for a single backend instance
 * (current Render setup). The HMAC key is random per start-up: pending codes do not survive
 * a restart anyway, so there is no secret to configure. With several instances move this to
 * Redis/DB and use a shared secret.
 */
@Service
public class OtpService {

    /** Why the code was issued. A code is only valid for the purpose it was issued for. */
    public enum Purpose { SIGNUP, PASSWORD_RESET }

    public enum Result { OK, INVALID, EXPIRED_OR_MISSING, TOO_MANY_ATTEMPTS }

    private static final int OTP_LENGTH = 6;

    private static final class Entry {
        final byte[] otpHash;
        final long expiresAtMs;
        int failedAttempts;

        Entry(byte[] otpHash, long expiresAtMs) {
            this.otpHash = otpHash;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private final SecureRandom random = new SecureRandom();
    private final byte[] hmacKey = new byte[32];

    /** Active (not yet consumed / expired) OTPs. */
    private final ConcurrentMap<String, Entry> store = new ConcurrentHashMap<>();

    /** When the last OTP was issued for each key; drives the resend cooldown. */
    private final ConcurrentMap<String, Long> lastIssuedAtMs = new ConcurrentHashMap<>();

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
        random.nextBytes(hmacKey);
    }

    /**
     * Seconds the caller must still wait before another OTP may be issued
     * for this purpose and email (0 = may issue now).
     */
    public long secondsUntilResendAllowed(Purpose purpose, String email) {
        Long issuedAt = lastIssuedAtMs.get(key(purpose, email));
        if (issuedAt == null) {
            return 0;
        }
        long waitMs = issuedAt + resendCooldownMs - clock.getAsLong();
        return waitMs <= 0 ? 0 : (waitMs + 999) / 1000;
    }

    /**
     * Creates and stores a fresh 6-digit OTP for this purpose and email, replacing any
     * previous one, and returns the plain code so it can be emailed. Only its hash is kept.
     */
    public String issue(Purpose purpose, String email) {
        purgeExpired();

        String key = key(purpose, email);
        long now = clock.getAsLong();

        String otp = String.format("%0" + OTP_LENGTH + "d", random.nextInt(1_000_000));

        store.put(key, new Entry(hash(key, otp), now + ttlMs));
        lastIssuedAtMs.put(key, now);

        return otp;
    }

    /** Checks the OTP. A successful check consumes it (single use). */
    public Result verifyAndConsume(Purpose purpose, String email, String candidate) {
        String key = key(purpose, email);

        Entry entry = store.get(key);
        if (entry == null) {
            return Result.EXPIRED_OR_MISSING;
        }

        synchronized (entry) {
            // Another thread may have consumed or replaced it while we waited for the lock
            if (store.get(key) != entry) {
                return Result.EXPIRED_OR_MISSING;
            }

            if (clock.getAsLong() > entry.expiresAtMs) {
                store.remove(key, entry);
                return Result.EXPIRED_OR_MISSING;
            }

            if (entry.failedAttempts >= maxAttempts) {
                store.remove(key, entry);
                return Result.TOO_MANY_ATTEMPTS;
            }

            boolean matches = isWellFormed(candidate)
                    && MessageDigest.isEqual(entry.otpHash, hash(key, candidate.trim()));

            if (matches) {
                store.remove(key, entry);
                return Result.OK;
            }

            entry.failedAttempts++;
            if (entry.failedAttempts >= maxAttempts) {
                store.remove(key, entry);
                return Result.TOO_MANY_ATTEMPTS;
            }
            return Result.INVALID;
        }
    }

    /**
     * Drops an OTP and its cooldown, e.g. when the email could not be sent, so the user
     * is not made to wait for a code they never received.
     */
    public void discard(Purpose purpose, String email) {
        String key = key(purpose, email);
        store.remove(key);
        lastIssuedAtMs.remove(key);
    }

    // ------------------------------------------------------------------

    /** Purpose + lower-cased, trimmed email: "SIGNUP:user@example.com". */
    private static String key(Purpose purpose, String email) {
        String normalised = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return purpose.name() + ":" + normalised;
    }

    private static boolean isWellFormed(String candidate) {
        if (candidate == null) {
            return false;
        }
        String trimmed = candidate.trim();
        if (trimmed.length() != OTP_LENGTH) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** HMAC-SHA256(key = random per-start secret, data = "<purpose>:<email>:<otp>"). */
    private byte[] hash(String key, String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); // Mac is not thread-safe: one per call
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return mac.doFinal((key + ":" + otp).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            // HmacSHA256 is required on every JVM; this cannot happen in practice
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private void purgeExpired() {
        long now = clock.getAsLong();
        store.entrySet().removeIf(e -> now > e.getValue().expiresAtMs);
        lastIssuedAtMs.entrySet().removeIf(e -> now > e.getValue() + Math.max(ttlMs, resendCooldownMs));
    }
}