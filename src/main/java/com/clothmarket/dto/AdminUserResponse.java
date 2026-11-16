package com.clothmarket.dto;

import com.clothmarket.model.User;
import com.clothmarket.service.MediaUrlSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One account as the admin panel sees it. There is deliberately no password / hash field here.
 * postCount is null in list responses (no extra query per row) and filled in on the detail view.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {

    private Long id;
    private String name;
    private String email;
    private String mobile;
    private String location;
    @JsonSerialize(using = MediaUrlSerializer.class)
    private String profileImage;
    private LocalDateTime createdAt;
    private boolean blocked;
    private boolean superAdmin;
    private Long postCount;

    public static AdminUserResponse from(User user, boolean superAdmin, Long postCount) {
        return new AdminUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getMobile(),
                user.getLocation(),
                user.getProfileImage(),
                user.getCreatedAt(),
                Boolean.TRUE.equals(user.getBlocked()),
                superAdmin,
                postCount);
    }
}