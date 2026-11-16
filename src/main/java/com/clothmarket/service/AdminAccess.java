package com.clothmarket.service;

import com.clothmarket.exception.ApiException;
import com.clothmarket.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The single place that decides who is an administrator.
 *
 * There is no admin role in the data model: the Super Admins are the user ids listed in the
 * ADMIN_USER_IDS environment variable (comma separated). It is read once at start-up and no API
 * can change it, so no request, profile edit or database row can grant admin rights.
 * Empty = nobody is an admin and every admin endpoint answers 403.
 */
@Component
public class AdminAccess {

    private final Set<Long> adminIds;

    public AdminAccess(@Value("${admin.user-ids:}") String rawUserIds) {
        this.adminIds = Arrays.stream(rawUserIds.split(","))
                .map(String::trim)
                .filter(s -> s.matches("\\d+"))
                .map(Long::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** True when this user id is a Super Admin. */
    public boolean isSuperAdmin(Long userId) {
        return userId != null && adminIds.contains(userId);
    }

    /**
     * Throws 403 unless the authenticated caller (taken from the verified JWT, never from the
     * request) is a Super Admin. Returns the caller's user id.
     */
    public Long requireAdmin() {
        Long callerId = CurrentUser.id();
        if (!isSuperAdmin(callerId)) {
            throw ApiException.forbidden("Access denied");
        }
        return callerId;
    }
}