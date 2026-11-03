package com.clothmarket.controller;

import com.clothmarket.dto.CallSignal;
import com.clothmarket.repository.UserRepository;
import com.clothmarket.security.StompAuthChannelInterceptor;
import com.clothmarket.service.BlockService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.Set;

/**
 * Relays WebRTC signaling (offer/answer/ICE/reject/end) to the target user's
 * private topic. Nothing is persisted.
 *
 * Security: the sender identity comes from the authenticated STOMP session, never
 * from the message body, and the caller's display name/avatar are looked up in
 * the database, so a client cannot impersonate another user or fake a caller name.
 */
@Controller
@RequiredArgsConstructor
public class CallSignalController {

    private static final Logger log = LoggerFactory.getLogger(CallSignalController.class);

    private static final Set<String> SIGNAL_TYPES = Set.of(
            "call-invite", "call-answer", "call-reject", "call-end", "ice-candidate");

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final BlockService blockService;

    @Value("${websocket.auth-required:true}")
    private boolean authRequired;

    @MessageMapping("/call/signal")
    public void handleCallSignal(CallSignal signal, SimpMessageHeaderAccessor headers) {
        if (signal == null || signal.getType() == null || !SIGNAL_TYPES.contains(signal.getType())) {
            return;
        }

        Long authenticatedId = null;
        Map<String, Object> attrs = headers.getSessionAttributes();
        if (attrs != null && attrs.get(StompAuthChannelInterceptor.USER_ID_ATTR) instanceof Long id) {
            authenticatedId = id;
        }

        if (authenticatedId != null) {
            signal.setFromUserId(authenticatedId);   // never trust the client's value
        } else if (authRequired) {
            log.warn("Dropped call signal from an unauthenticated session");
            return;
        }

        Long from = signal.getFromUserId();
        Long to = signal.getToUserId();
        if (from == null || to == null || from.equals(to)) {
            return;
        }

        // Deleted accounts (token still valid) and blocked pairs cannot signal each other.
        if (blockService.isBlockedEitherWay(from, to)) {
            return;
        }
        if (authenticatedId != null && !userRepository.existsById(authenticatedId)) {
            return;
        }

        if (!"video".equals(signal.getCallType())) {
            signal.setCallType("audio");
        }

        if ("call-invite".equals(signal.getType())) {
            userRepository.findById(from).ifPresent(caller -> {
                signal.setFromUserName(caller.getName());
                signal.setProfileImage(caller.getProfileImage());
            });
        } else {
            signal.setFromUserName(null);
            signal.setProfileImage(null);
        }

        messagingTemplate.convertAndSend("/topic/call/" + to, signal);
    }
}
