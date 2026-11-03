package com.clothmarket.security;

import java.security.Principal;

/** The authenticated user attached to a STOMP session. getName() is the user id. */
public record StompPrincipal(Long userId) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
