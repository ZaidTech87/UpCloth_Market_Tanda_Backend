package com.clothmarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * What the admin panel may change on a user. Roles, the blocked flag and the password are NOT
 * here, so no request can set them. Email and mobile are optional: blank means "leave unchanged".
 * Their format and uniqueness are checked in AdminPanelService.
 */
@Data
public class AdminUserUpdateRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name is too long")
    private String name;

    @NotBlank(message = "Location is required")
    @Size(max = 150, message = "Location is too long")
    private String location;

    @Size(max = 254, message = "Email is too long")
    private String email;

    @Size(max = 15, message = "Mobile number is too long")
    private String mobile;
}