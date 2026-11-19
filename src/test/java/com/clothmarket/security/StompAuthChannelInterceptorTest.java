package com.clothmarket.security;

import com.clothmarket.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompAuthChannelInterceptorTest {

    private JwtUtil jwt;
    private UserRepository users;
    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", "unit-test-secret-unit-test-secret-1234");
        ReflectionTestUtils.setField(jwt, "expiration", 60_000L);
        jwt.init();
        users = mock(UserRepository.class);
        // User 7 exists and is not blocked; every other id is unknown (mock default: false).
        when(users.existsActiveById(7L)).thenReturn(true);
        interceptor = new StompAuthChannelInterceptor(jwt, users, true);
    }

    private Message<?> frame(StompCommand cmd, String session, String destination, String bearer) {
        StompHeaderAccessor a = StompHeaderAccessor.create(cmd);
        a.setSessionId(session);
        a.setSessionAttributes(new HashMap<>());
        if (destination != null) a.setDestination(destination);
        if (bearer != null) a.addNativeHeader("Authorization", "Bearer " + bearer);
        a.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], a.getMessageHeaders());
    }

    @Test
    void connectWithoutOrWithBadTokenIsRejected() {
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, "s1", null, null), null));
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, "s1", null, "nope"), null));
    }

    @Test
    void connectedUserMaySubscribeOnlyToOwnTopics() {
        String token = jwt.generateToken(7L, "7");
        interceptor.preSend(frame(StompCommand.CONNECT, "s1", null, token), null);

        assertDoesNotThrow(() ->
                interceptor.preSend(frame(StompCommand.SUBSCRIBE, "s1", "/topic/messages/7", null), null));
        assertDoesNotThrow(() ->
                interceptor.preSend(frame(StompCommand.SUBSCRIBE, "s1", "/topic/call/7", null), null));
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.SUBSCRIBE, "s1", "/topic/messages/8", null), null));
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.SUBSCRIBE, "s1", "/topic/call/8", null), null));
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.SUBSCRIBE, "s1", "/topic/**", null), null));
    }

    @Test
    void connectedUserMaySubscribeOnlyToOwnMessageStatusTopic() {
        String token = jwt.generateToken(7L, "7");
        interceptor.preSend(frame(StompCommand.CONNECT, "s1", null, token), null);

        assertDoesNotThrow(() ->
                interceptor.preSend(frame(StompCommand.SUBSCRIBE, "s1", "/topic/message-status/7", null), null));
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.SUBSCRIBE, "s1", "/topic/message-status/8", null), null));
    }

    @Test
    void clientsCannotPublishDirectlyToTopics() {
        String token = jwt.generateToken(7L, "7");
        interceptor.preSend(frame(StompCommand.CONNECT, "s1", null, token), null);

        assertDoesNotThrow(() ->
                interceptor.preSend(frame(StompCommand.SEND, "s1", "/app/call/signal", null), null));
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.SEND, "s1", "/topic/messages/8", null), null));
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.SEND, "s1", "/topic/call/8", null), null));
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.SEND, "s1", "/topic/message-status/8", null), null));
    }

    @Test
    void unauthenticatedSessionCannotSubscribeOrSend() {
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.SUBSCRIBE, "ghost", "/topic/messages/1", null), null));
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.SEND, "ghost", "/app/call/signal", null), null));
    }

    @Test
    void blockedOrDeletedAccountCannotConnectEvenWithAValidToken() {
        // A correctly signed token for a user the repository does not consider active.
        String token = jwt.generateToken(8L, "8");
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, "s2", null, token), null));
    }

    @Test
    void userBlockedAfterConnectingCannotSubscribeOrSendAnyMore() {
        String token = jwt.generateToken(7L, "7");
        interceptor.preSend(frame(StompCommand.CONNECT, "s1", null, token), null);
        assertDoesNotThrow(() ->
                interceptor.preSend(frame(StompCommand.SUBSCRIBE, "s1", "/topic/messages/7", null), null));

        // An administrator blocks the user while the socket is open.
        when(users.existsActiveById(7L)).thenReturn(false);

        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.SUBSCRIBE, "s1", "/topic/call/7", null), null));
        assertThrows(MessagingException.class, () ->
                interceptor.preSend(frame(StompCommand.SEND, "s1", "/app/call/signal", null), null));
    }
}