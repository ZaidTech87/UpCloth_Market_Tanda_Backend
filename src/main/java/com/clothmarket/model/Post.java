package com.clothmarket.model;
import com.clothmarket.service.MediaUrlSerializer;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;

@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_posts_created_at", columnList = "created_at"),
        @Index(name = "idx_posts_user_created", columnList = "user_id, created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Stored value is the permanent R2 object key (never a signed URL). It is converted to a
    // short-lived signed URL only when the JSON response is written.
    @JsonSerialize(using = MediaUrlSerializer.class)
    @Column(name = "media_url")
    private String mediaUrl;

    @Column(name = "media_type")
    private String mediaType; // "image" or "video"

    private Double price;

    private Integer quantity;

    @Column(name = "cloth_type")
    private String clothType;

    @Column(name = "created_at")
    @JsonFormat(
            pattern = "yyyy-MM-dd'T'HH:mm:ss",
            timezone = "UTC"
    )
    private LocalDateTime createdAt;

    @Transient
    private String userName;

    @Transient
    private String userLocation;

    // Copy of the poster's profile image key; resolved to a signed URL when serialized.
    @JsonSerialize(using = MediaUrlSerializer.class)
    @Transient
    private String userProfileImage;


    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.ofInstant(
                Instant.now(),
                ZoneOffset.UTC
        );
    }
}