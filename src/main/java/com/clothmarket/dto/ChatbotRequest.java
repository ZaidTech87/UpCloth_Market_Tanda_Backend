package com.clothmarket.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChatbotRequest {
    private String message;
    private List<ChatTurn> history; // pichli conversation, optional

    @Data
    public static class ChatTurn {
        private String role;    // "user" ya "assistant"
        private String content;
    }
}