package com.clothmarket.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class LoginRequest {
    @NotBlank(message = "Mobile number is required")
    @Size(max = 20, message = "Invalid mobile number")
    private String mobile;

    @NotBlank(message = "Password is required")
    @Size(max = 128, message = "Invalid password")
    private String password;
}
