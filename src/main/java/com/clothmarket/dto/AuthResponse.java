package com.clothmarket.dto;

import com.clothmarket.service.MediaUrlSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String token;
    private Long userId;
    private String name;
    private String mobile;
    private String location;
    // Holds the permanent R2 object key; converted to a short-lived signed URL when serialized.
    @JsonSerialize(using = MediaUrlSerializer.class)
    private String profileImage;
    private String message;
}