package com.clothmarket.service;

import com.clothmarket.repository.MessageRepository;
import com.clothmarket.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class MessageServiceUnreadTest {

    private MessageRepository messages;
    private BlockService blocks;
    private MessageService service;

    @BeforeEach
    void setUp() {
        messages = mock(MessageRepository.class);
        blocks = mock(BlockService.class);
        service = new MessageService(messages, mock(UserRepository.class), mock(StorageService.class),
                mock(SimpMessagingTemplate.class), blocks);
    }

    @Test
    void withNoBlocksTheTotalUnreadMessageCountIsReturned() {
        when(blocks.relatedIds(7L)).thenReturn(Set.of());
        when(messages.countTotalUnread(7L)).thenReturn(12L);

        assertEquals(12L, service.getUnreadCount(7L));
        verify(messages, never()).countTotalUnreadExcludingSenders(anyLong(), any());
    }

    @Test
    void unreadFromBlockedUsersIsExcludedSoTheBadgeMatchesTheInbox() {
        when(blocks.relatedIds(7L)).thenReturn(Set.of(3L));
        when(messages.countTotalUnreadExcludingSenders(7L, Set.of(3L))).thenReturn(4L);

        assertEquals(4L, service.getUnreadCount(7L));
        verify(messages, never()).countTotalUnread(anyLong());
    }
}