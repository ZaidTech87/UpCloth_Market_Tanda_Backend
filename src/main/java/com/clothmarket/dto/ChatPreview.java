package com.clothmarket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatPreview {
    private PublicUser user;
    private String lastMessage;      // text preview, ya voice message ke liye "Voice message"
    private String lastMessageType;  // "text" or "voice"
    private LocalDateTime lastMessageTime;
    private long unreadCount;
    private boolean lastMessageMine; // true agar last message maine bheja tha
}