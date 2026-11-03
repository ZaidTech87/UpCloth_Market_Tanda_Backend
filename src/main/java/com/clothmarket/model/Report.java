package com.clothmarket.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A user-submitted report about a user, a post or a message (UGC safety). */
@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_status_created", columnList = "status, created_at"),
        @Index(name = "idx_reports_reporter_created", columnList = "reporter_id, created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id")
    private Long reporterId;

    /** USER, POST or MESSAGE */
    @Column(name = "target_type", nullable = false, length = 16)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** The account responsible for the reported content (kept as a bare id). */
    @Column(name = "reported_user_id")
    private Long reportedUserId;

    @Column(nullable = false, length = 32)
    private String reason;

    @Column(length = 500)
    private String details;

    /** OPEN or RESOLVED */
    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "resolution", length = 32)
    private String resolution;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
