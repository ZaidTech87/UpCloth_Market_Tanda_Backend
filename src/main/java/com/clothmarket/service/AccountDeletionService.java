package com.clothmarket.service;

import com.clothmarket.exception.ApiException;
import com.clothmarket.model.User;
import com.clothmarket.repository.MessageRepository;
import com.clothmarket.repository.PostRepository;
import com.clothmarket.repository.ReportRepository;
import com.clothmarket.repository.UserBlockRepository;
import com.clothmarket.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Permanently deletes an account and the data tied to it:
 *  - profile, posts, every message sent or received (incl. voice notes),
 *    blocks involving the user, and reports the user filed;
 *  - the uploaded media files (profile image, post media, voice notes).
 *
 * Retained (documented in the privacy policy): reports filed ABOUT the user by
 * others keep only the bare numeric user id, for safety/legal obligations.
 *
 * Database changes run in ONE transaction (all or nothing). Media files are
 * removed afterwards, best effort: a failed file deletion is logged and never
 * resurrects the account.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository users;
    private final PostRepository posts;
    private final MessageRepository messages;
    private final ReportRepository reports;
    private final UserBlockRepository blocks;
    private final StorageService storage;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;
    private final TransactionTemplate tx;

    public AccountDeletionService(UserRepository users, PostRepository posts, MessageRepository messages,
                                  ReportRepository reports, UserBlockRepository blocks, StorageService storage,
                                  PasswordEncoder passwordEncoder, LoginAttemptService loginAttempts,
                                  TransactionTemplate tx) {
        this.users = users;
        this.posts = posts;
        this.messages = messages;
        this.reports = reports;
        this.blocks = blocks;
        this.storage = storage;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
        this.tx = tx;
    }

    /** User-initiated deletion: requires the current password. */
    public void deleteAccount(Long userId, String password) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));

        // Re-use the login lockout so this endpoint cannot be used to guess the password.
        if (loginAttempts.isLocked(user.getMobile())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Please try again later.");
        }
        if (password == null || password.isEmpty()
                || !passwordEncoder.matches(password, user.getPassword())) {
            loginAttempts.recordFailure(user.getMobile());
            throw ApiException.badRequest("Incorrect password");
        }
        loginAttempts.recordSuccess(user.getMobile());

        purge(user);
    }

    /** Moderator-initiated removal (no password). Callers must have authorised the actor. */
    public void deleteAccountAsModerator(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        purge(user);
    }

    private void purge(User user) {
        Long userId = user.getId();

        // 1. Collect every media URL BEFORE the rows disappear.
        Set<String> mediaUrls = new LinkedHashSet<>();
        addIfPresent(mediaUrls, user.getProfileImage());
        mediaUrls.addAll(posts.findMediaUrlsByUser(userId));
        mediaUrls.addAll(messages.findVoiceUrlsInvolving(userId));

        // 2. All database deletions in a single transaction.
        tx.executeWithoutResult(status -> {
            messages.deleteAllInvolving(userId);
            posts.deleteAllByUser(userId);
            reports.deleteByReporter(userId);
            blocks.deleteAllInvolving(userId);
            users.deleteById(userId);
        });

        loginAttempts.recordSuccess(user.getMobile());

        // 3. Media files, best effort.
        List<String> failed = new ArrayList<>();
        for (String url : mediaUrls) {
            try {
                storage.deleteMedia(url);
            } catch (Exception e) {
                failed.add(url);
            }
        }
        log.info("Account {} deleted ({} media files, {} could not be removed)",
                userId, mediaUrls.size(), failed.size());
    }

    private static void addIfPresent(Set<String> set, String value) {
        if (value != null && !value.isBlank()) {
            set.add(value);
        }
    }
}
