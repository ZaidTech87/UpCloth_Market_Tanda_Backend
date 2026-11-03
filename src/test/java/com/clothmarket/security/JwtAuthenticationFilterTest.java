package com.clothmarket.security;

import com.clothmarket.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JwtUtil jwtUtil;
    private UserRepository users;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtUtil = mock(JwtUtil.class);
        users = mock(UserRepository.class);
        filter = new JwtAuthenticationFilter(jwtUtil, users);
        when(jwtUtil.authenticate("good")).thenReturn(Optional.of(new AuthenticatedUser(5L, "9999999999")));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void run(String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));
    }

    @Test
    void validTokenOfExistingUserAuthenticates() throws Exception {
        when(users.existsById(5L)).thenReturn(true);
        run("good");
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(5L, CurrentUser.id());
    }

    @Test
    void validTokenOfDeletedAccountDoesNotAuthenticate() throws Exception {
        when(users.existsById(5L)).thenReturn(false);
        run("good");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void invalidTokenDoesNotAuthenticate() throws Exception {
        when(jwtUtil.authenticate("bad")).thenReturn(Optional.empty());
        run("bad");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
