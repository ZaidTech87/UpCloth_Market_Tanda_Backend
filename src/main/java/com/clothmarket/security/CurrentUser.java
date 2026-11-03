package com.clothmarket.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Small helper so controllers can do:
 *   Long realUserId = CurrentUser.id();
 * instead of trusting a userId query/form param sent by the client.
 */
public final class CurrentUser {

    private CurrentUser() {}

    public static AuthenticatedUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new RuntimeException("Not authenticated");
        }
        return user;
    }

    public static Long id() {
        return get().userId();
    }
}
