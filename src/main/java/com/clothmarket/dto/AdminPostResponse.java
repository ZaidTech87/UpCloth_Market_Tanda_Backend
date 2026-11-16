package com.clothmarket.dto;

import com.clothmarket.model.Post;
import com.clothmarket.model.User;
import com.clothmarket.service.MediaUrlSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One post as the admin panel sees it, with its owner's contact details (admins only). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPostResponse {

    private Long id;
    private String description;
    // Stored value is the permanent R2 key; written as a short-lived signed URL.
    @JsonSerialize(using = MediaUrlSerializer.class)
    private String mediaUrl;
    private String mediaType;
    private Double price;
    private Integer quantity;
    private String clothType;
    private LocalDateTime createdAt;
    private Owner owner;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Owner {
        private Long id;
        private String name;
        private String email;
        private String mobile;
        private String location;
        @JsonSerialize(using = MediaUrlSerializer.class)
        private String profileImage;

        public static Owner from(User user) {
            if (user == null) {
                return null;
            }
            return new Owner(user.getId(), user.getName(), user.getEmail(), user.getMobile(),
                    user.getLocation(), user.getProfileImage());
        }
    }

    /** The post's owner must already be loaded (PostRepository.searchForAdmin / findByIdWithOwner). */
    public static AdminPostResponse from(Post post) {
        return new AdminPostResponse(
                post.getId(),
                post.getDescription(),
                post.getMediaUrl(),
                post.getMediaType(),
                post.getPrice(),
                post.getQuantity(),
                post.getClothType(),
                post.getCreatedAt(),
                Owner.from(post.getUser()));
    }
}