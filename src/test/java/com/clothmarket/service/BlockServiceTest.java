package com.clothmarket.service;

import com.clothmarket.exception.ApiException;
import com.clothmarket.model.UserBlock;
import com.clothmarket.repository.UserBlockRepository;
import com.clothmarket.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BlockServiceTest {

    private UserBlockRepository blocks;
    private UserRepository users;
    private BlockService service;

    @BeforeEach
    void setUp() {
        blocks = mock(UserBlockRepository.class);
        users = mock(UserRepository.class);
        service = new BlockService(blocks, users);
    }

    @Test
    void blockIsStoredOnceAndNotForSelfOrUnknownUsers() {
        when(users.existsById(2L)).thenReturn(true);
        service.block(1L, 2L);
        verify(blocks).save(any(UserBlock.class));

        when(blocks.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);
        service.block(1L, 2L);
        verify(blocks, times(1)).save(any(UserBlock.class));

        assertThrows(ApiException.class, () -> service.block(1L, 1L));
        assertThrows(ApiException.class, () -> service.block(1L, 3L));
    }

    @Test
    void blockedEitherWayChecksBothDirections() {
        when(blocks.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(true);
        assertTrue(service.isBlockedEitherWay(1L, 2L));
        assertTrue(service.isBlockedEitherWay(2L, 1L));
        assertFalse(service.isBlockedEitherWay(1L, 3L));
        assertFalse(service.isBlockedEitherWay(null, 3L));
    }

    @Test
    void relatedIdsComeFromRepository() {
        when(blocks.findRelatedUserIds(1L)).thenReturn(List.of(2L, 3L));
        assertEquals(Set.of(2L, 3L), service.relatedIds(1L));
    }
}
