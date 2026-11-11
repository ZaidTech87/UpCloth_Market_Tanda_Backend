package com.clothmarket.controller;

import com.clothmarket.dto.AuthResponse;
import com.clothmarket.dto.LoginRequest;
import com.clothmarket.dto.SignUpRequest;
import com.clothmarket.exception.ApiException;
import com.clothmarket.model.User;
import com.clothmarket.repository.UserRepository;
import com.clothmarket.service.AuthService;
import com.clothmarket.service.EmailService;
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

    // Same email rule as SignUpRequest
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    // Same policy as signup
    private static final Pattern PASSWORD_POLICY = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{6,72}$");

    private static final String FORGOT_PASSWORD_REPLY =
            "If this email is registered, a reset OTP has been sent to it.";

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
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

    // ================= SIGNUP: PROVE EMAIL OWNERSHIP =================
    /**
     * Step 1 of signup. Emails a 6-digit OTP so only the owner of the address can register it.
     * When signup.require-otp=false this does nothing and answers {"otpRequired": false}
     * so clients can go straight to /auth/signup.
     */
    @PostMapping("/signup/send-otp")
    public ResponseEntity<Map<String, Object>> sendSignupOtp(@RequestBody Map<String, String> request) {
        if (!signupRequireOtp) {
            return ResponseEntity.ok(Map.of("otpRequired", false));
        }
        String email = AuthService.normalizeEmail(request.get("email"));
        if (!isValidEmail(email)) {
            throw ApiException.badRequest("Enter a valid email address");
        }
        if (userRepository.existsByEmail(email)) {
            throw ApiException.badRequest("Email already registered");
        }
        issueAndSendOtp(OtpService.Purpose.SIGNUP, email);
        return ResponseEntity.ok(Map.of("otpRequired", true, "message", "OTP sent"));
    }

    // ================= LOGIN =================
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // ================= FORGOT PASSWORD =================
    /**
     * Emails a reset OTP if the address belongs to an account. The reply is identical whether or
     * not the email is registered (and even if sending fails or the cooldown is active), so
     * this endpoint cannot be used to find out which emails have accounts.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody Map<String, String> request) {
        String email = AuthService.normalizeEmail(request.get("email"));
        if (email.isEmpty()) {
            throw ApiException.badRequest("Email is required");
        }
        if (!isValidEmail(email)) {
            throw ApiException.badRequest("Enter a valid email address");
        }

        if (userRepository.findByEmail(email).isPresent()) {
            try {
                issueAndSendOtp(OtpService.Purpose.PASSWORD_RESET, email);
            } catch (ApiException e) {
                // Cooldown or delivery problem: log it, but do not let the response differ.
                log.warn("Password reset OTP not sent: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(FORGOT_PASSWORD_REPLY);
    }

    // ================= RESET PASSWORD =================
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody Map<String, String> request) {
        String email = AuthService.normalizeEmail(request.get("email"));
        String otp = clean(request.get("otp"));
        String newPassword = request.get("newPassword");

        if (email.isEmpty()) {
            throw ApiException.badRequest("Email is required");
        }
        if (otp.isEmpty()) {
            throw ApiException.badRequest("OTP is required");
        }
        if (newPassword == null || !PASSWORD_POLICY.matcher(newPassword).matches()) {
            throw ApiException.badRequest(
                    "Password must be 6-72 characters and contain at least one letter and one number");
        }

        OtpService.Result result = otpService.verifyAndConsume(OtpService.Purpose.PASSWORD_RESET, email, otp);
        switch (result) {
            case OK -> { /* continue */ }
            case INVALID -> throw ApiException.badRequest("Invalid OTP");
            case TOO_MANY_ATTEMPTS -> throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. Please request a new OTP.");
            default -> throw ApiException.badRequest(
                    "OTP expired or not requested. Please request a new one.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.badRequest("Invalid OTP"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        loginAttemptService.recordSuccess(email);

        return ResponseEntity.ok("Password reset successful");
    }

    /**
     * Issues an OTP for the given purpose and emails it. Enforces the resend cooldown.
     * Signup and reset OTPs are stored separately, so one can never be used for the other.
     */
    private void issueAndSendOtp(OtpService.Purpose purpose, String email) {
        long wait = otpService.secondsUntilResendAllowed(purpose, email);
        if (wait > 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait " + wait + " seconds before requesting another OTP");
        }

        String otp = otpService.issue(purpose, email);

        // DEVELOPMENT MODE: print the OTP instead of emailing it.
        if (logOtpToConsole) {
            log.warn("DEV OTP ({}) generated for {}: {}", purpose, email, otp);
            return;
        }

        if (!emailService.isConfigured()) {
            otpService.discard(purpose, email);
            log.error("OTP requested but RESEND_API_KEY / MAIL_FROM are not configured");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not send the OTP right now. Please try again later.");
        }

        try {
            emailService.sendOtp(email, otp, purpose);
        } catch (Exception e) {
            otpService.discard(purpose, email);
            log.error("Could not send OTP email: {}", e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not send the OTP right now. Please try again later.");
        }
    }

    private static boolean isValidEmail(String email) {
        return email.length() <= 254 && EMAIL.matcher(email).matches();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}