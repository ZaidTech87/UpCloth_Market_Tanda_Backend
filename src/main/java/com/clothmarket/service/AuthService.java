package com.clothmarket.service;

import com.clothmarket.dto.AuthResponse;
import com.clothmarket.dto.LoginRequest;
import com.clothmarket.dto.SignUpRequest;
import com.clothmarket.exception.ApiException;
import com.clothmarket.model.User;
import com.clothmarket.repository.UserRepository;
import com.clothmarket.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    // Wrong email and wrong password get the same message so the response does not reveal which emails exist.
    private static final String BAD_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final OtpService otpService;

    @Value("${signup.require-otp:true}")
    private boolean requireOtp;

    @Value("${legal.require-consent:true}")
    private boolean requireConsent;

    @Value("${legal.terms-version:1}")
    private String termsVersion;

    /** Emails are stored and compared trimmed and lower-case. */
    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public AuthResponse signUp(SignUpRequest request) {
        String mobile = request.getMobile().trim();
        String email = normalizeEmail(request.getEmail());

        if (requireConsent && !Boolean.TRUE.equals(request.getAcceptTerms())) {
            throw ApiException.badRequest(
                    "You must accept the Terms and Privacy Policy and confirm you are 18 or older");
        }

        if (userRepository.existsByEmail(email)) {
            throw ApiException.badRequest("Email already registered");
        }
        if (userRepository.existsByMobile(mobile)) {
            throw ApiException.badRequest("Mobile number already registered");
        }

        if (requireOtp) {
            OtpService.Result otpResult =
                    otpService.verifyAndConsume(OtpService.Purpose.SIGNUP, email, request.getOtp());
            switch (otpResult) {
                case OK -> { /* email ownership proven */ }
                case INVALID -> throw ApiException.badRequest("Invalid OTP");
                case TOO_MANY_ATTEMPTS -> throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many incorrect attempts. Please request a new OTP.");
                default -> throw ApiException.badRequest(
                        "OTP expired or not requested. Please request a new one.");
            }
        }

        User user = new User();
        if (Boolean.TRUE.equals(request.getAcceptTerms())) {
            user.setTermsVersion(termsVersion);
            user.setTermsAcceptedAt(java.time.LocalDateTime.now());
        }
        user.setName(request.getName().trim());
        user.setLocation(request.getLocation().trim());
        user.setMobile(mobile);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Two simultaneous signups for the same email or number: the unique index wins.
            throw ApiException.badRequest("Email or mobile number already registered");
        }

        return toResponse(user, "User registered successfully");
    }

    /** Email + password only. No OTP at login. */
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (loginAttemptService.isLocked(email)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Please try again later or reset your password.");
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailure(email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }

        loginAttemptService.recordSuccess(email);
        return toResponse(user, "Login successful");
    }

    private AuthResponse toResponse(User user, String message) {
        // Unchanged: the JWT still carries the user id and mobile, so existing tokens and sessions keep working.
        String token = jwtUtil.generateToken(user.getId(), user.getMobile());
        return new AuthResponse(
                token,
                user.getId(),
                user.getName(),
                user.getMobile(),
                user.getEmail(),
                user.getLocation(),
                user.getProfileImage(),
                message
        );
    }
}