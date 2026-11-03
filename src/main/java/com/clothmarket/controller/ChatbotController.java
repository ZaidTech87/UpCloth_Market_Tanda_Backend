package com.clothmarket.controller;

import com.clothmarket.dto.ChatbotRequest;
import com.clothmarket.dto.ChatbotResponse;
import com.clothmarket.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/ask")
    public ResponseEntity<ChatbotResponse> ask(@RequestBody ChatbotRequest request) {
        String reply = chatbotService.getReply(request);
        return ResponseEntity.ok(new ChatbotResponse(reply));
    }
}