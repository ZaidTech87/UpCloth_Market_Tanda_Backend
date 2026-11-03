package com.clothmarket.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class OtpServiceTest {

    private final AtomicLong now = new AtomicLong(1_000_000);

    private OtpService service(int maxAttempts) {
        return new OtpService(300, maxAttempts, 60, now::get);
    }

    private static String wrongOtp(String real) {
        return "000000".equals(real) ? "111111" : "000000";
    }

    @Test
    void issuesSixDigitOtpAndEnforcesResendCooldown() {
        OtpService s = service(3);
        String otp = s.issue("9999999999");
        assertTrue(otp.matches("\\d{6}"));
        assertEquals(60, s.secondsUntilResendAllowed("9999999999"));
        now.addAndGet(61_000);
        assertEquals(0, s.secondsUntilResendAllowed("9999999999"));
    }

    @Test
    void correctOtpWorksOnceOnly() {
        OtpService s = service(3);
        String otp = s.issue("9999999999");
        assertEquals(OtpService.Result.INVALID, s.verifyAndConsume("9999999999", wrongOtp(otp)));
        assertEquals(OtpService.Result.OK, s.verifyAndConsume("9999999999", otp));
        assertEquals(OtpService.Result.EXPIRED_OR_MISSING, s.verifyAndConsume("9999999999", otp));
    }

    @Test
    void tooManyWrongAttemptsDestroyTheOtp() {
        OtpService s = service(3);
        String otp = s.issue("1111111111");
        String bad = wrongOtp(otp);
        s.verifyAndConsume("1111111111", bad);
        s.verifyAndConsume("1111111111", bad);
        assertEquals(OtpService.Result.TOO_MANY_ATTEMPTS, s.verifyAndConsume("1111111111", bad));
        assertEquals(OtpService.Result.EXPIRED_OR_MISSING, s.verifyAndConsume("1111111111", otp));
    }

    @Test
    void expiredOrUnknownOtpIsRejected() {
        OtpService s = service(3);
        String otp = s.issue("2222222222");
        now.addAndGet(301_000);
        assertEquals(OtpService.Result.EXPIRED_OR_MISSING, s.verifyAndConsume("2222222222", otp));
        assertEquals(OtpService.Result.EXPIRED_OR_MISSING, s.verifyAndConsume("3333333333", "123456"));
    }
}
