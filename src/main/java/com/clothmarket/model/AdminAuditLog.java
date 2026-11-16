package com.clothmarket.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One row per destructive or sensitive admin action. Deliberately stores IDs and short
 * non-personal details only (never names, emails, mobile numbers or message content), so the log
 * does not keep personal data about an account that was deleted. There is no foreign key to
 * users, so the history survives account deletion.
 */
@Entity
@Table(name = "admin_audit_log", indexes = {
        @Index(name = "idx_admin_audit_created", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLog {

    public static final String POST_DELETE = "POST_DELETE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_BLOCK = "USER_BLOCK";
    public static final String USER_UNBLOCK = "USER_UNBLOCK";
    public static final String USER_DELETE = "USER_DELETE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The administrator who acted (from the verified JWT). */
    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(nullable = false, length = 40)
    private String action;

    /** "POST" or "USER". */
    @Column(name = "target_type", length = 20)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    /** Free-text reason typed by the admin (optional, max 500). */
    @Column(length = 500)
    private String reason;

    /** Short, non-personal facts, e.g. "ownerId=5 mediaType=image" or "fields=name,location". */
    @Column(length = 500)
    private String details;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}