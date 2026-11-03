package com.clothmarket.model;

import com.clothmarket.service.MediaUrlSerializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false, unique = true)
    private String mobile;

    // The BCrypt hash must never be serialised into any API response or WebSocket frame.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String password;

    // Stored value is the permanent R2 object key (never a signed URL). It is converted to a
    // short-lived signed URL only when the JSON response is written.
    @JsonSerialize(using = MediaUrlSerializer.class)
    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** Which version of the Terms/Privacy Policy the user accepted, and when. */
    @JsonIgnore
    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @JsonIgnore
    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Post> posts;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}