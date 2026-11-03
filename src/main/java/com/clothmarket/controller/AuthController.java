package com.clothmarket.controller;

import com.clothmarket.dto.AuthResponse;
import com.clothmarket.dto.LoginRequest;
import com.clothmarket.dto.SignUpRequest;
import com.clothmarket.exception.ApiException;
import com.clothmarket.model.User;
import com.clothmarket.repository.UserRepository;
import com.clothmarket.service.AuthService;
import com.clothmarket.service.Fast2SmsService;
import com.clothmarket.service.LoginAttemptService;
import com.clothmarket.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private static final Pattern MOBILE = Pattern.compile("^[0-9]{10,15}$");
    // Same policy as signup
    private static final Pattern PASSWORD_POLICY = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{6,72}$");

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Fast2SmsService fast2SmsService;
    private final OtpService otpService;
    private final LoginAttemptService loginAttemptService;

    @Value("${otp.log-to-console:false}")
    private boolean logOtpToConsole;

    @Value("${signup.require-otp:true}")
    private boolean signupRequireOtp;

    // ================= SIGNUP =================
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.ok(authService.signUp(request));
    }

    // ================= SIGNUP: PROVE MOBILE OWNERSHIP =================
    /**
     * Step 1 of signup. Sends an OTP to the number so only its owner can register it.
     * When signup.require-otp=false this does nothing and answers {"otpRequired": false}
     * so clients can go straight to /auth/signup.
     */
    @PostMapping("/signup/send-otp")
    public ResponseEntity<Map<String, Object>> sendSignupOtp(@RequestBody Map<String, String> request) {
        if (!signupRequireOtp) {
            return ResponseEntity.ok(Map.of("otpRequired", false));
        }
        String mobile = clean(request.get("mobile"));
        if (!MOBILE.matcher(mobile).matches()) {
            throw ApiException.badRequest("Enter a valid mobile number (10-15 digits)");
        }
        if (userRepository.existsByMobile(mobile)) {
            throw ApiException.badRequest("Mobile number already registered");
        }
        issueAndSendOtp(AuthService.SIGNUP_OTP_PREFIX + mobile, mobile);
        return ResponseEntity.ok(Map.of("otpRequired", true, "message", "OTP sent"));
    }

    // ================= LOGIN =================
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // ================= FORGOT PASSWORD =================
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody Map<String, String> request) {
        String mobile = clean(request.get("mobile"));
        userRepository.findAll().forEach(user ->
                log.info("Backend DB stored mobile: [{}]", user.getMobile())
        );

        if (mobile.isEmpty()) {
            throw ApiException.badRequest("Mobile number is required");
        }
        if (!MOBILE.matcher(mobile).matches()) {
            throw ApiException.badRequest("Enter a valid mobile number (10-15 digits)");
        }

        // NOTE: kept for API compatibility with the existing clients. It does
        // reveal whether a number is registered (signup does too); rate limiting
        // bounds the abuse. See PENDING_WORK.md.
        log.info("Forgot password mobile received: [{}]", mobile);

        log.info("Total users visible to backend: {}",
                userRepository.count());

        log.info("Mobile exists in backend database: {}",
                userRepository.existsByMobile(mobile));

        User user = userRepository.findByMobile(mobile)
                .orElseThrow(() -> ApiException.badRequest("User not found"));

        issueAndSendOtp(mobile, mobile);

        return ResponseEntity.ok("OTP sent");
    }

    // ================= RESET PASSWORD =================
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody Map<String, String> request) {
        String mobile = clean(request.get("mobile"));
        String otp = clean(request.get("otp"));
        String newPassword = request.get("newPassword");

        if (mobile.isEmpty()) {
            throw ApiException.badRequest("Mobile number is required");
        }
        if (otp.isEmpty()) {
            throw ApiException.badRequest("OTP is required");
        }
        if (newPassword == null || !PASSWORD_POLICY.matcher(newPassword).matches()) {
            throw ApiException.badRequest(
                    "Password must be 6-72 characters and contain at least one letter and one number");
        }

        OtpService.Result result = otpService.verifyAndConsume(mobile, otp);
        switch (result) {
            case OK -> { /* continue */ }
            case INVALID -> throw ApiException.badRequest("Invalid OTP");
            case TOO_MANY_ATTEMPTS -> throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. Please request a new OTP.");
            default -> throw ApiException.badRequest(
                    "OTP expired or not requested. Please request a new one.");
        }

        User user = userRepository.findByMobile(mobile)
                .orElseThrow(() -> ApiException.badRequest("Invalid OTP"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        loginAttemptService.recordSuccess(mobile);

        return ResponseEntity.ok("Password reset successful");
    }

    /**
     * Issues an OTP stored under {@code otpKey} (the mobile number for password reset,
     * "signup:" + mobile for signup, so the two can never be mixed up) and SMSes it
     * to {@code mobile}. Enforces the resend cooldown.
     */

    private void issueAndSendOtp(String otpKey, String mobile) {

        long wait = otpService.secondsUntilResendAllowed(otpKey);

        if (wait > 0) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait " + wait
                            + " seconds before requesting another OTP"
            );
        }

        String otp = otpService.issue(otpKey);

        // DEVELOPMENT MODE: Never call Fast2SMS.
        if (logOtpToConsole) {

            log.warn("DEV OTP generated for {}: {}", mobile, otp);

            return;
        }

        // PRODUCTION MODE: Send OTP through Fast2SMS.
        if (fast2SmsService.isConfigured()) {

            try {
                fast2SmsService.sendOtp(mobile, otp);

            } catch (Exception e) {

                otpService.discard(otpKey);

                log.error(
                        "Could not send OTP SMS: {}",
                        e.getClass().getSimpleName()
                );

                throw new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Could not send the OTP right now. Please try again later."
                );
            }

        } else {

            otpService.discard(otpKey);

            log.error("OTP requested but FAST2SMS_API_KEY is not configured");

            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not send the OTP right now. Please try again later."
            );
        }
    }


    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
