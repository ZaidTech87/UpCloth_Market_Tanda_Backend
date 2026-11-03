package com.clothmarket.service;

import com.clothmarket.exception.ApiException;
import com.clothmarket.model.User;
import com.clothmarket.repository.MessageRepository;
import com.clothmarket.repository.PostRepository;
import com.clothmarket.repository.ReportRepository;
import com.clothmarket.repository.UserBlockRepository;
import com.clothmarket.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountDeletionServiceTest {

    private UserRepository users;
    private PostRepository posts;
    private MessageRepository messages;
    private ReportRepository reports;
    private UserBlockRepository blocks;
    private StorageService storage;
    private PasswordEncoder encoder;
    private LoginAttemptService attempts;
    private TransactionTemplate tx;
    private AccountDeletionService service;
    private User user;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        users = mock(UserRepository.class);
        posts = mock(PostRepository.class);
        messages = mock(MessageRepository.class);
        reports = mock(ReportRepository.class);
        blocks = mock(UserBlockRepository.class);
        storage = mock(StorageService.class);
        encoder = mock(PasswordEncoder.class);
        attempts = mock(LoginAttemptService.class);
        tx = mock(TransactionTemplate.class);
        // run the transactional block immediately
        doAnswer(i -> { ((Consumer<Object>) i.getArgument(0)).accept(null); return null; })
                .when(tx).executeWithoutResult(any());

        user = new User();
        user.setId(5L);
        user.setMobile("9999999999");
        user.setPassword("HASH");
        user.setProfileImage("/uploads/profiles/me.jpg");
        when(users.findById(5L)).thenReturn(Optional.of(user));
        when(posts.findMediaUrlsByUser(5L)).thenReturn(List.of("/uploads/posts/p.jpg"));
        when(messages.findVoiceUrlsInvolving(5L)).thenReturn(List.of("/uploads/voice/v.m4a"));

        service = new AccountDeletionService(users, posts, messages, reports, blocks, storage, encoder, attempts, tx);
    }

    @Test
    void wrongPasswordDeletesNothingAndCountsAsFailedAttempt() {
        when(encoder.matches("bad", "HASH")).thenReturn(false);

        assertThrows(ApiException.class, () -> service.deleteAccount(5L, "bad"));

        verify(attempts).recordFailure("9999999999");
        verifyNoInteractions(messages, posts, blocks, storage);
        verify(users, never()).deleteById(any());
    }

    @Test
    void lockedAccountCannotAttemptDeletion() {
        when(attempts.isLocked("9999999999")).thenReturn(true);
        assertThrows(ApiException.class, () -> service.deleteAccount(5L, "whatever"));
        verify(users, never()).deleteById(any());
    }

    @Test
    void correctPasswordRemovesAllDataThenMediaFiles() throws Exception {
        when(encoder.matches("good", "HASH")).thenReturn(true);

        service.deleteAccount(5L, "good");

        InOrder order = inOrder(messages, posts, reports, blocks, users);
        order.verify(messages).deleteAllInvolving(5L);
        order.verify(posts).deleteAllByUser(5L);
        order.verify(reports).deleteByReporter(5L);
        order.verify(blocks).deleteAllInvolving(5L);
        order.verify(users).deleteById(5L);

        verify(storage).deleteMedia("/uploads/profiles/me.jpg");
        verify(storage).deleteMedia("/uploads/posts/p.jpg");
        verify(storage).deleteMedia("/uploads/voice/v.m4a");
    }

    @Test
    void failedMediaDeletionDoesNotUndoOrFailTheDeletion() throws Exception {
        when(encoder.matches("good", "HASH")).thenReturn(true);
        doThrow(new java.io.IOException("cloud down")).when(storage).deleteMedia(anyString());

        assertDoesNotThrow(() -> service.deleteAccount(5L, "good"));
        verify(users).deleteById(5L);
    }
}
