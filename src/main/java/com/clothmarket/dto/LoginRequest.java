package com.clothmarket.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

@Data
public class LoginRequest {
    /** Trimmed and lower-cased as soon as it is set (see setEmail), to match how emails are stored. */
    @NotBlank(message = "Email is required")
    @Size(max = 254, message = "Invalid email")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(max = 128, message = "Invalid password")
    private String password;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}