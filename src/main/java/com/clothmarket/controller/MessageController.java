package com.clothmarket.controller;

import com.clothmarket.dto.ChatPreview;
import com.clothmarket.dto.PublicUser;
import com.clothmarket.exception.ApiException;
import com.clothmarket.model.Message;
import com.clothmarket.security.CurrentUser;
import com.clothmarket.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Every endpoint derives "who am I" from the verified JWT (CurrentUser), never from a
 * client-supplied user id, so users can only read/write their own conversations.
 */
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/send/text")
    public ResponseEntity<Message> sendTextMessage(
            @RequestParam Long receiverId,
            @RequestParam String message) {
        return ResponseEntity.ok(
                messageService.sendTextMessage(CurrentUser.id(), receiverId, message));
    }

    @PostMapping("/send/voice")
    public ResponseEntity<Message> sendVoiceMessage(
            @RequestParam Long receiverId,
            @RequestParam("file") MultipartFile voiceFile) throws IOException {
        return ResponseEntity.ok(
                messageService.sendVoiceMessage(CurrentUser.id(), receiverId, voiceFile));
    }

    @GetMapping("/chat")
    public ResponseEntity<List<Message>> getChatMessages(@RequestParam Long otherUserId) {
        return ResponseEntity.ok(messageService.getChatMessages(CurrentUser.id(), otherUserId));
    }

    @GetMapping("/chat-users/{userId}")
    public ResponseEntity<List<PublicUser>> getChatUsers(@PathVariable Long userId) {
        requireSelf(userId);
        return ResponseEntity.ok(messageService.getChatUsers(userId).stream()
                .map(PublicUser::from).toList());
    }

    @GetMapping("/inbox/{userId}")
    public ResponseEntity<List<ChatPreview>> getInbox(@PathVariable Long userId) {
        requireSelf(userId);
        return ResponseEntity.ok(messageService.getInbox(userId));
    }

    @GetMapping("/unread-count/{userId}")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@PathVariable Long userId) {
        requireSelf(userId);
        return ResponseEntity.ok(Map.of("count", messageService.getUnreadCount(userId)));
    }

    /** The reader is always the authenticated user. */
    @PostMapping("/mark-read")
    public ResponseEntity<Void> markAsRead(@RequestParam Long fromUserId) {
        messageService.markAsRead(fromUserId, CurrentUser.id());
        return ResponseEntity.ok().build();
    }

    private static void requireSelf(Long pathUserId) {
        if (!CurrentUser.id().equals(pathUserId)) {
            throw ApiException.forbidden("You can only access your own data");
        }
    }
}
