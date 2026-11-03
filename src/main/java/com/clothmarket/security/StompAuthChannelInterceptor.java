package com.clothmarket.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Security for the STOMP layer (clientInboundChannel).
 *
 *  CONNECT    must carry "Authorization: Bearer <jwt>"; the user is then bound to the session.
 *  SUBSCRIBE  only to /topic/messages/{ownUserId} and /topic/call/{ownUserId}.
 *  SEND       only to /app/call/signal. Clients can NOT publish straight to /topic/**
 *             (the simple broker would relay it, letting anyone forge messages/calls
 *             to any user).
 *
 * The authenticated user id is also stored in the session attributes under
 * {@link #USER_ID_ATTR} so @MessageMapping handlers can read it.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    public static final String USER_ID_ATTR = "clothmarket.userId";

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private static final Pattern OWN_TOPIC =
            Pattern.compile("^/topic/(messages|call)/(\\d+)$");

    private static final String CALL_SIGNAL_DESTINATION = "/app/call/signal";

    private final JwtUtil jwtUtil;
    private final boolean authRequired;

    /** sessionId -> user, kept as a fallback in case the principal header is not propagated. */
    private final Map<String, Long> sessionUsers = new ConcurrentHashMap<>();

    public StompAuthChannelInterceptor(JwtUtil jwtUtil,
                                       @Value("${websocket.auth-required:true}") boolean authRequired) {
        this.jwtUtil = jwtUtil;
        this.authRequired = authRequired;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command)) {
            onConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            onSubscribe(accessor);
        } else if (StompCommand.SEND.equals(command)) {
            onSend(accessor);
        }
        return message;
    }

    private void onConnect(StompHeaderAccessor accessor) {
        String header = firstNonNull(
                accessor.getFirstNativeHeader("Authorization"),
                accessor.getFirstNativeHeader("authorization"));

        Optional<AuthenticatedUser> user = Optional.empty();
        if (header != null) {
            String token = header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
            user = jwtUtil.authenticate(token);
        }

        if (user.isPresent()) {
            Long userId = user.get().userId();
            accessor.setUser(new StompPrincipal(userId));
            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs != null) {
                attrs.put(USER_ID_ATTR, userId);
            }
            if (accessor.getSessionId() != null) {
                sessionUsers.put(accessor.getSessionId(), userId);
            }
            return;
        }

        if (authRequired) {
            log.warn("Rejected STOMP CONNECT: missing or invalid JWT");
            throw new MessagingException("Unauthorized");
        }
        log.warn("STOMP CONNECT without JWT accepted because websocket.auth-required=false");
    }

    private void onSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        Long userId = userIdOf(accessor);

        if (userId == null) {
            if (authRequired) {
                throw new MessagingException("Unauthorized");
            }
            return; // legacy mode: cannot check ownership without an identity
        }

        Matcher m = destination == null ? null : OWN_TOPIC.matcher(destination);
        if (m == null || !m.matches() || !String.valueOf(userId).equals(m.group(2))) {
            log.warn("User {} denied SUBSCRIBE to {}", userId, destination);
            throw new MessagingException("Forbidden destination");
        }
    }

    private void onSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        Long userId = userIdOf(accessor);

        if (userId == null && authRequired) {
            throw new MessagingException("Unauthorized");
        }
        if (!CALL_SIGNAL_DESTINATION.equals(destination)) {
            log.warn("User {} denied SEND to {}", userId, destination);
            throw new MessagingException("Forbidden destination");
        }
        // Make sure handlers can see the identity even if the principal header was not propagated.
        if (userId != null) {
            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs != null) {
                attrs.putIfAbsent(USER_ID_ATTR, userId);
            }
        }
    }

    private Long userIdOf(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof StompPrincipal p) {
            return p.userId();
        }
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null && attrs.get(USER_ID_ATTR) instanceof Long id) {
            return id;
        }
        return accessor.getSessionId() == null ? null : sessionUsers.get(accessor.getSessionId());
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        sessionUsers.remove(event.getSessionId());
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
