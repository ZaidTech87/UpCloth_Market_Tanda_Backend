package com.clothmarket.service;

import com.clothmarket.dto.ChatPreview;
import com.clothmarket.dto.PublicUser;
import com.clothmarket.exception.ApiException;
import com.clothmarket.model.Message;
import com.clothmarket.model.User;
import com.clothmarket.repository.MessageRepository;
import com.clothmarket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    static final int MAX_TEXT_LENGTH = 2000;

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final BlockService blockService;

    public Message sendTextMessage(Long senderId, Long receiverId, String messageText) {
        String text = messageText == null ? "" : messageText.trim();
        if (text.isEmpty()) {
            throw ApiException.badRequest("Message cannot be empty");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw ApiException.badRequest("Message is too long (max " + MAX_TEXT_LENGTH + " characters)");
        }
        requireValidReceiver(senderId, receiverId);

        Message message = new Message();
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setMessage(text);
        message.setMessageType("text");

        Message saved = messageRepository.save(message);
        // Real-time push to the receiver (they subscribe to /topic/messages/{theirId}).
        messagingTemplate.convertAndSend("/topic/messages/" + receiverId, saved);
        return saved;
    }

    public Message sendVoiceMessage(Long senderId, Long receiverId, MultipartFile voiceFile) throws IOException {
        requireValidReceiver(senderId, receiverId);

        // messages.voice_url stores the permanent R2 object key, never a signed URL.
        String voiceKey = storageService.store(voiceFile, "voice", MediaKind.AUDIO);

        Message message = new Message();
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setVoiceUrl(voiceKey);
        message.setMessageType("voice");

        Message saved;
        try {
            saved = messageRepository.save(message);
        } catch (RuntimeException e) {
            // The message was not saved: do not leave the uploaded audio orphaned in R2.
            storageService.deleteQuietly(voiceKey);
            throw e;
        }

        // The message and its audio are saved. A failure to push it in real time (for example
        // while producing the signed URL) must not fail the request, or the client would retry
        // and send a duplicate; the receiver still gets it when they load the chat.
        try {
            messagingTemplate.convertAndSend("/topic/messages/" + receiverId, saved);
        } catch (RuntimeException e) {
            log.warn("Real-time push of a voice message failed (receiverId={}, cause={})",
                    receiverId, e.getClass().getSimpleName());
        }
        return saved;
    }

    public List<Message> getChatMessages(Long userId1, Long userId2) {
        List<Message> messages = messageRepository.findChatMessages(userId1, userId2);

        // Two lookups for the whole conversation instead of two per message.
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(List.of(userId1, userId2))
                .forEach(u -> names.put(u.getId(), u.getName()));

        messages.forEach(message -> {
            message.setSenderName(names.get(message.getSenderId()));
            message.setReceiverName(names.get(message.getReceiverId()));
        });

        return messages;
    }

    public List<User> getChatUsers(Long userId) {
        java.util.Set<Long> hidden = blockService.relatedIds(userId);
        List<Long> userIds = messageRepository.findChatUserIds(userId).stream()
                .filter(id -> !hidden.contains(id)).toList();
        return new ArrayList<>(userRepository.findAllById(userIds));
    }

    /**
     * Inbox: one entry per chat partner with the last message and unread count,
     * most recent first.
     */
    public List<ChatPreview> getInbox(Long userId) {
        java.util.Set<Long> hidden = blockService.relatedIds(userId);
        List<Long> partnerIds = messageRepository.findChatUserIds(userId).stream()
                .filter(id -> !hidden.contains(id)).toList();
        Map<Long, User> partners = userRepository.findAllById(partnerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<ChatPreview> previews = new ArrayList<>();

        for (Long partnerId : partnerIds) {
            User partner = partners.get(partnerId);
            if (partner == null) continue;

            List<Message> latest = messageRepository.findLatestInChat(userId, partnerId, PageRequest.of(0, 1));
            if (latest.isEmpty()) continue;

            Message last = latest.get(0);
            long unread = messageRepository.countUnreadInChat(partnerId, userId);

            String preview = "text".equals(last.getMessageType())
                    ? last.getMessage()
                    : "Voice message";

            previews.add(new ChatPreview(
                    PublicUser.from(partner),
                    preview,
                    last.getMessageType(),
                    last.getCreatedAt(),
                    unread,
                    last.getSenderId().equals(userId)
            ));
        }

        previews.sort(Comparator.comparing(ChatPreview::getLastMessageTime).reversed());
        return previews;
    }

    /**
     * Total unread MESSAGES (not conversations) addressed to the user. Messages from users the
     * inbox hides (blocked either way) are left out, so this always equals the sum of the unread
     * counts shown in the inbox and the badge can always be cleared by reading the inbox.
     */
    public long getUnreadCount(Long userId) {
        java.util.Set<Long> hidden = blockService.relatedIds(userId);
        if (hidden.isEmpty()) {
            return messageRepository.countTotalUnread(userId);
        }
        return messageRepository.countTotalUnreadExcludingSenders(userId, hidden);
    }

    @Transactional
    public void markAsRead(Long fromUserId, Long toUserId) {
        messageRepository.markAsRead(fromUserId, toUserId);
    }

    private void requireValidReceiver(Long senderId, Long receiverId) {
        if (receiverId == null || receiverId.equals(senderId)) {
            throw ApiException.badRequest("Invalid recipient");
        }
        if (!userRepository.existsById(senderId)) {
            throw ApiException.forbidden("Account no longer exists");
        }
        if (!userRepository.existsById(receiverId)) {
            throw ApiException.notFound("Recipient not found");
        }
        if (blockService.isBlockedEitherWay(senderId, receiverId)) {
            // 409, not 403: the mobile app treats 401/403 as "session expired" and logs out.
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "You cannot message this user");
        }
    }
}