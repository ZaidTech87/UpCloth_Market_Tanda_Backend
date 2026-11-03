package com.clothmarket.security;

/**
 * Represents the currently logged-in user, resolved from a verified JWT.
 * Used by controllers/services to know WHO is really making the request,
 * instead of trusting a userId sent by the client (fixes Step 2 - IDOR).
 */
public record AuthenticatedUser(Long userId, String mobile) {
}
