package com.clothmarket.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static JwtUtil util(String secret, long expirationMs) {
        JwtUtil u = new JwtUtil();
        ReflectionTestUtils.setField(u, "secret", secret);
        ReflectionTestUtils.setField(u, "expiration", expirationMs);
        u.init();
        return u;
    }

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    @Test
    void roundTripsUserId() {
        JwtUtil u = util(SECRET, 60_000);
        AuthenticatedUser user = u.authenticate(u.generateToken(42L, "9999999999")).orElseThrow();
        assertEquals(42L, user.userId());
        assertEquals("9999999999", user.mobile());
    }

    @Test
    void rejectsTamperedExpiredForeignAndGarbageTokens() {
        JwtUtil u = util(SECRET, 60_000);
        String token = u.generateToken(1L, "1");
        assertTrue(u.authenticate(token + "x").isEmpty());
        assertTrue(u.authenticate("garbage").isEmpty());
        assertTrue(u.authenticate(null).isEmpty());
        assertTrue(util(SECRET, -1000).authenticate(util(SECRET, -1000).generateToken(1L, "1")).isEmpty());
        JwtUtil other = util("another-secret-another-secret-another-1", 60_000);
        assertTrue(u.authenticate(other.generateToken(1L, "1")).isEmpty());
    }

    @Test
    void refusesToStartWithWeakSecret() {
        assertThrows(IllegalStateException.class, () -> util("short", 1000));
        assertThrows(IllegalStateException.class, () -> util(null, 1000));
    }
}
