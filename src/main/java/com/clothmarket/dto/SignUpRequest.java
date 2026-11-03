package com.clothmarket.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
public class SignUpRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name is too long")
    private String name;

    @NotBlank(message = "Location is required")
    @Size(max = 150, message = "Location is too long")
    private String location;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[0-9]{10,15}$", message = "Enter a valid mobile number (10-15 digits)")
    private String mobile;

    // At least one letter and one digit, 6-72 chars (72 = BCrypt's input limit)
    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 72, message = "Password must be 6 to 72 characters")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
        message = "Password must contain at least one letter and one number"
    )
    private String password;

    /**
     * The user ticked "I agree to the Terms and Privacy Policy and I am 18 or older".
     * Enforced in AuthService when legal.require-consent=true (default).
     */
    private Boolean acceptTerms;

    /** One-time password sent by POST /auth/signup/send-otp (required when signup.require-otp=true). */
    @Size(max = 10, message = "Invalid OTP")
    private String otp;
}
