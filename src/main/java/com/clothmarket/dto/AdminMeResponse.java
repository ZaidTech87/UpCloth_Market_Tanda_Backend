package com.clothmarket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Answer to GET /admin/me: proves the caller is an administrator. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminMeResponse {
    private Long userId;
    private String name;
    private String email;
    private String role;
}