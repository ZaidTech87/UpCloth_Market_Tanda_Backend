package com.clothmarket.service;

import com.clothmarket.dto.SignUpRequest;
import com.clothmarket.exception.ApiException;
import com.clothmarket.model.User;
import com.clothmarket.repository.UserRepository;
import com.clothmarket.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceSignupTest {

    private UserRepository users;
    private OtpService otp;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        otp = mock(OtpService.class);
        JwtUtil jwt = mock(JwtUtil.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("HASH");
        when(jwt.generateToken(any(), any())).thenReturn("tok");
        when(users.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(1L);
            return u;
        });
        service = new AuthService(users, jwt, encoder, mock(LoginAttemptService.class), otp);
        ReflectionTestUtils.setField(service, "requireOtp", true);
        ReflectionTestUtils.setField(service, "requireConsent", true);
        ReflectionTestUtils.setField(service, "termsVersion", "1");
    }

    private static SignUpRequest req(Boolean accept, String code) {
        SignUpRequest r = new SignUpRequest();
        r.setName("A");
        r.setLocation("B");
        r.setMobile("9999999999");
        r.setPassword("abc123");
        r.setAcceptTerms(accept);
        r.setOtp(code);
        return r;
    }

    @Test
    void signupWithoutConsentIsRejectedBeforeAnyOtpIsConsumed() {
        assertThrows(ApiException.class, () -> service.signUp(req(false, "123456")));
        verifyNoInteractions(otp);
        verify(users, never()).save(any(User.class));
    }

    @Test
    void wrongOrMissingOtpIsRejected() {
        when(otp.verifyAndConsume("signup:9999999999", "000000")).thenReturn(OtpService.Result.INVALID);
        when(otp.verifyAndConsume("signup:9999999999", null)).thenReturn(OtpService.Result.EXPIRED_OR_MISSING);
        assertThrows(ApiException.class, () -> service.signUp(req(true, "000000")));
        assertThrows(ApiException.class, () -> service.signUp(req(true, null)));
        verify(users, never()).save(any(User.class));
    }

    @Test
    void correctOtpCreatesAccountAndRecordsConsent() {
        when(otp.verifyAndConsume("signup:9999999999", "123456")).thenReturn(OtpService.Result.OK);
        var res = service.signUp(req(true, "123456"));
        assertEquals("tok", res.getToken());
        verify(users).save(argThat(u -> "1".equals(u.getTermsVersion()) && u.getTermsAcceptedAt() != null));
    }

    @Test
    void otpCheckCanBeSwitchedOff() {
        ReflectionTestUtils.setField(service, "requireOtp", false);
        assertNotNull(service.signUp(req(true, null)));
        verifyNoInteractions(otp);
    }
}
