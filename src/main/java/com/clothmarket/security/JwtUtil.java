package com.clothmarket.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Creates and validates JWTs (HS256).
 *
 * The signing secret must come from the JWT_SECRET environment variable and
 * be at least 32 characters; the application fails fast at startup otherwise,
 * so a missing/weak secret can never silently reach production.
 */
@Component
public class JwtUtil {

    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private Key signingKey;

    @PostConstruct
    void init() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + MIN_SECRET_LENGTH + " characters long");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String mobile) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("mobile", mobile);

        Date now = new Date();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(mobile)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expiration))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Verifies signature + expiry and returns the claims. Throws JwtException on any problem. */
    public Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Parses a token into an {@link AuthenticatedUser}, or empty if the token is
     * missing, malformed, tampered with or expired. Never throws.
     */
    public Optional<AuthenticatedUser> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = extractClaims(token);
            Object rawId = claims.get("userId");
            if (!(rawId instanceof Number number)) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(number.longValue(), claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Long extractUserId(String token) {
        Object rawId = extractClaims(token).get("userId");
        return rawId instanceof Number number ? number.longValue() : null;
    }

    public String extractMobile(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractClaims(token).getExpiration().before(new Date());
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return true;
        }
    }

    public boolean validateToken(String token, String mobile) {
        return extractMobile(token).equals(mobile) && !isTokenExpired(token);
    }
}
