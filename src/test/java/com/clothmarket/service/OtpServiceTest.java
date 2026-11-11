package com.clothmarket.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class OtpServiceTest {

    private static final String EMAIL = "buyer@example.com";
    private static final String OTHER_EMAIL = "seller@example.com";
    private static final OtpService.Purpose SIGNUP = OtpService.Purpose.SIGNUP;
    private static final OtpService.Purpose RESET = OtpService.Purpose.PASSWORD_RESET;

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
        assertEquals(0, s.secondsUntilResendAllowed(SIGNUP, EMAIL));
        String otp = s.issue(SIGNUP, EMAIL);
        assertTrue(otp.matches("\\d{6}"));
        assertEquals(60, s.secondsUntilResendAllowed(SIGNUP, EMAIL));
        now.addAndGet(61_000);
        assertEquals(0, s.secondsUntilResendAllowed(SIGNUP, EMAIL));
    }

    @Test
    void cooldownSurvivesSuccessfulVerification() {
        OtpService s = service(3);
        String otp = s.issue(SIGNUP, EMAIL);
        assertEquals(OtpService.Result.OK, s.verifyAndConsume(SIGNUP, EMAIL, otp));
        assertTrue(s.secondsUntilResendAllowed(SIGNUP, EMAIL) > 0);
    }

    @Test
    void discardClearsOtpAndCooldown() {
        OtpService s = service(3);
        String otp = s.issue(SIGNUP, EMAIL);
        s.discard(SIGNUP, EMAIL);
        assertEquals(0, s.secondsUntilResendAllowed(SIGNUP, EMAIL));
        assertEquals(OtpService.Result.EXPIRED_OR_MISSING, s.verifyAndConsume(SIGNUP, EMAIL, otp));
    }

    @Test
    void correctOtpWorksOnceOnly() {
        OtpService s = service(3);
        String otp = s.issue(SIGNUP, EMAIL);
        assertEquals(OtpService.Result.INVALID, s.verifyAndConsume(SIGNUP, EMAIL, wrongOtp(otp)));
        assertEquals(OtpService.Result.OK, s.verifyAndConsume(SIGNUP, EMAIL, otp));
        assertEquals(OtpService.Result.EXPIRED_OR_MISSING, s.verifyAndConsume(SIGNUP, EMAIL, otp));
    }

    @Test
    void tooManyWrongAttemptsDestroyTheOtp() {
        OtpService s = service(3);
        String otp = s.issue(SIGNUP, EMAIL);
        String bad = wrongOtp(otp);
        s.verifyAndConsume(SIGNUP, EMAIL, bad);
        s.verifyAndConsume(SIGNUP, EMAIL, bad);
        assertEquals(OtpService.Result.TOO_MANY_ATTEMPTS, s.verifyAndConsume(SIGNUP, EMAIL, bad));
        // Even the correct code no longer works once the attempts are used up.
        assertEquals(OtpService.Result.EXPIRED_OR_MISSING, s.verifyAndConsume(SIGNUP, EMAIL, otp));
    }

    @Test
    void expiredOrUnknownOtpIsRejected() {
        OtpService s = service(3);
        String otp = s.issue(SIGNUP, EMAIL);
        now.addAndGet(301_000);
        assertEquals(OtpService.Result.EXPIRED_OR_MISSING, s.verifyAndConsume(SIGNUP, EMAIL, otp));
        assertEquals(OtpService.Result.EXPIRED_OR_MISSING, s.verifyAndConsume(SIGNUP, OTHER_EMAIL, "123456"));
    }

    @Test
    void otpForOnePurposeCannotBeUsedForTheOther() {
        OtpService s = service(3);
        String signupOtp = s.issue(SIGNUP, EMAIL);
        assertEquals(OtpService.Result.EXPIRED_OR_MISSING, s.verifyAndConsume(RESET, EMAIL, signupOtp));
        // The failed cross-purpose try must not have consumed the real OTP.
        assertEquals(OtpService.Result.OK, s.verifyAndConsume(SIGNUP, EMAIL, signupOtp));
    }

    @Test
    void purposesHaveIndependentCooldowns() {
        OtpService s = service(3);
        s.issue(SIGNUP, EMAIL);
        assertEquals(0, s.secondsUntilResendAllowed(RESET, EMAIL));
    }

    @Test
    void otpIsBoundToTheEmailItWasIssuedFor() {
        OtpService s = service(3);
        String otp = s.issue(SIGNUP, EMAIL);
        assertEquals(OtpService.Result.EXPIRED_OR_MISSING, s.verifyAndConsume(SIGNUP, OTHER_EMAIL, otp));
        assertEquals(OtpService.Result.OK, s.verifyAndConsume(SIGNUP, EMAIL, otp));
    }

    @Test
    void emailIsMatchedIgnoringCaseAndSurroundingSpaces() {
        OtpService s = service(3);
        String otp = s.issue(SIGNUP, "  Buyer@Example.COM ");
        assertTrue(s.secondsUntilResendAllowed(SIGNUP, EMAIL) > 0);
        assertEquals(OtpService.Result.OK, s.verifyAndConsume(SIGNUP, EMAIL, otp));
    }

    @Test
    void malformedCodesCountAsWrongAttempts() {
        OtpService s = service(3);
        String otp = s.issue(SIGNUP, EMAIL);
        assertEquals(OtpService.Result.INVALID, s.verifyAndConsume(SIGNUP, EMAIL, null));
        assertEquals(OtpService.Result.INVALID, s.verifyAndConsume(SIGNUP, EMAIL, "12ab56"));
        assertEquals(OtpService.Result.TOO_MANY_ATTEMPTS, s.verifyAndConsume(SIGNUP, EMAIL, "12345"));
        assertEquals(OtpService.Result.EXPIRED_OR_MISSING, s.verifyAndConsume(SIGNUP, EMAIL, otp));
    }

    @Test
    void newOtpReplacesThePreviousOne() {
        OtpService s = service(3);
        String first = s.issue(SIGNUP, EMAIL);
        now.addAndGet(61_000);
        String second = s.issue(SIGNUP, EMAIL);
        assumeFalse(first.equals(second)); // 1-in-a-million chance of a repeat; skip rather than flake
        assertEquals(OtpService.Result.INVALID, s.verifyAndConsume(SIGNUP, EMAIL, first));
        assertEquals(OtpService.Result.OK, s.verifyAndConsume(SIGNUP, EMAIL, second));
    }
}