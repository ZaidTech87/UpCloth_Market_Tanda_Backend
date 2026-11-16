package com.clothmarket.service;

import com.clothmarket.exception.ApiException;
import com.clothmarket.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminAccessTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId, "9000000000"), null, List.of()));
    }

    @Test
    void listedIdsAreSuperAdmins() {
        AdminAccess access = new AdminAccess("1,7");
        assertTrue(access.isSuperAdmin(1L));
        assertTrue(access.isSuperAdmin(7L));
    }

    @Test
    void unlistedIdIsNotSuperAdmin() {
        AdminAccess access = new AdminAccess("1,7");
        assertFalse(access.isSuperAdmin(2L));
    }

    @Test
    void emptyConfigurationMeansNobodyIsAdmin() {
        AdminAccess access = new AdminAccess("");
        assertFalse(access.isSuperAdmin(1L));
    }

    @Test
    void requireAdminReturnsTheCallersIdForASuperAdmin() {
        AdminAccess access = new AdminAccess("7");
        loginAs(7L);
        assertEquals(7L, access.requireAdmin());
    }

    @Test
    void requireAdminRejectsAnOrdinaryUserWith403() {
        AdminAccess access = new AdminAccess("7");
        loginAs(8L);
        ApiException e = assertThrows(ApiException.class, access::requireAdmin);
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }

    @Test
    void requireAdminRejectsEveryoneWhenNoAdminIsConfigured() {
        AdminAccess access = new AdminAccess("");
        loginAs(1L);
        ApiException e = assertThrows(ApiException.class, access::requireAdmin);
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }
}