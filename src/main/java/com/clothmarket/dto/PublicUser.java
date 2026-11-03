package com.clothmarket.dto;

import com.clothmarket.model.User;
import com.clothmarket.service.MediaUrlSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * What OTHER users are allowed to see about an account. It deliberately has no
 * mobile number (and no password hash). The owner's own full record is only
 * returned by endpoints that first check "id == authenticated user".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicUser {
    private Long id;
    private String name;
    private String location;
    // Holds the permanent R2 object key; converted to a short-lived signed URL when serialized.
    @JsonSerialize(using = MediaUrlSerializer.class)
    private String profileImage;
    private LocalDateTime createdAt;

    public static PublicUser from(User u) {
        return new PublicUser(u.getId(), u.getName(), u.getLocation(),
                u.getProfileImage(), u.getCreatedAt());
    }
}