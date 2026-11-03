package com.clothmarket.model;

import com.clothmarket.service.MediaUrlSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_messages_receiver_read", columnList = "receiver_id, is_read"),
        @Index(name = "idx_messages_pair_time", columnList = "sender_id, receiver_id, created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(columnDefinition = "TEXT")
    private String message;

    // Stored value is the permanent R2 object key (never a signed URL). It is converted to a
    // short-lived signed URL only when the JSON response / STOMP frame is written.
    @JsonSerialize(using = MediaUrlSerializer.class)
    @Column(name = "voice_url")
    private String voiceUrl;

    @Column(name = "message_type")
    private String messageType; // "text" or "voice"

    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Transient
    private String senderName;

    @Transient
    private String receiverName;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}