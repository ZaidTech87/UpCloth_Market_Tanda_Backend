package com.clothmarket.controller;

import com.clothmarket.dto.CallSignal;
import com.clothmarket.model.User;
import com.clothmarket.repository.UserRepository;
import com.clothmarket.security.StompAuthChannelInterceptor;
import com.clothmarket.service.BlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CallSignalControllerTest {

    private SimpMessagingTemplate template;
    private UserRepository users;
    private BlockService blocks;
    private CallSignalController controller;

    @BeforeEach
    void setUp() {
        template = mock(SimpMessagingTemplate.class);
        users = mock(UserRepository.class);
        blocks = mock(BlockService.class);
        when(users.existsById(any())).thenReturn(true);
        controller = new CallSignalController(template, users, blocks);
        ReflectionTestUtils.setField(controller, "authRequired", true);
    }

    private static SimpMessageHeaderAccessor session(Long userId) {
        SimpMessageHeaderAccessor h = SimpMessageHeaderAccessor.create();
        Map<String, Object> attrs = new HashMap<>();
        if (userId != null) attrs.put(StompAuthChannelInterceptor.USER_ID_ATTR, userId);
        h.setSessionAttributes(attrs);
        return h;
    }

    private static CallSignal invite(Long from, Long to) {
        CallSignal s = new CallSignal();
        s.setType("call-invite");
        s.setFromUserId(from);
        s.setToUserId(to);
        s.setFromUserName("FAKE NAME");
        s.setCallType("video");
        return s;
    }

    @Test
    void senderIdentityAndCallerNameAreStampedServerSide() {
        User alice = new User();
        alice.setId(1L);
        alice.setName("Alice");
        when(users.findById(1L)).thenReturn(Optional.of(alice));

        controller.handleCallSignal(invite(999L, 2L), session(1L)); // client claims to be 999

        ArgumentCaptor<CallSignal> sent = ArgumentCaptor.forClass(CallSignal.class);
        verify(template).convertAndSend(eq("/topic/call/2"), sent.capture());
        assertEquals(1L, sent.getValue().getFromUserId());
        assertEquals("Alice", sent.getValue().getFromUserName());
    }

    @Test
    void unauthenticatedSessionIsDropped() {
        controller.handleCallSignal(invite(1L, 2L), session(null));
        verify(template, never()).convertAndSend(org.mockito.ArgumentMatchers.anyString(), any(Object.class));
    }

    @Test
    void unknownTypeAndSelfCallAreDropped() {
        CallSignal bad = invite(1L, 2L);
        bad.setType("evil");
        controller.handleCallSignal(bad, session(1L));
        controller.handleCallSignal(invite(1L, 1L), session(1L));
        verify(template, never()).convertAndSend(org.mockito.ArgumentMatchers.anyString(), any(Object.class));
    }

    @Test
    void blockedPairCannotSignal() {
        when(blocks.isBlockedEitherWay(1L, 2L)).thenReturn(true);
        controller.handleCallSignal(invite(1L, 2L), session(1L));
        verify(template, never()).convertAndSend(org.mockito.ArgumentMatchers.anyString(), any(Object.class));
    }

    @Test
    void deletedAccountCannotSignal() {
        when(users.existsById(1L)).thenReturn(false);
        controller.handleCallSignal(invite(1L, 2L), session(1L));
        verify(template, never()).convertAndSend(org.mockito.ArgumentMatchers.anyString(), any(Object.class));
    }
}
