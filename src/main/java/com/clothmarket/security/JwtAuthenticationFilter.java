package com.clothmarket.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.clothmarket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Reads "Authorization: Bearer <jwt>", verifies it and puts the real
 * authenticated user into the SecurityContext, so controllers can use
 * {@link CurrentUser#id()} instead of trusting a client-supplied userId.
 *
 * Deliberately NOT a @Component: SecurityConfig creates it and adds it to
 * the Spring Security chain. Making it a bean would register it twice.
 *
 * An invalid/expired token simply leaves the request unauthenticated, so
 * protected endpoints reject it.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    /** Used to reject tokens whose account was deleted (a JWT stays cryptographically valid until it expires). */
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();

            jwtUtil.authenticate(token)
                    .filter(principal -> userRepository.existsById(principal.userId()))
                    .ifPresent(principal -> {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }
}
