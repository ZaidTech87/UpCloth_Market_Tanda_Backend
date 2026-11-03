package com.clothmarket.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-IP rate limit on the sensitive endpoints (login, signup, OTP, chatbot).
 *
 * The client IP is request.getRemoteAddr(). Behind Render's proxy,
 * server.forward-headers-strategy=native makes Tomcat replace it with the real
 * client address taken from X-Forwarded-For ONLY when the request came through a
 * trusted proxy. The old code read the X-Forwarded-For header directly, which any
 * client can forge to get a fresh bucket on every request.
 *
 * In-memory: right for one instance. Use a shared store (Redis) for several.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_IPS = 50_000;

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int perMinute;

    public RateLimitingFilter(@Value("${ratelimit.per-minute:10}") int perMinute) {
        this.perMinute = perMinute;
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(perMinute, Refill.greedy(perMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private boolean isRateLimited(String path) {
        return path.startsWith("/auth/login")
                || path.startsWith("/auth/signup")
                || path.startsWith("/auth/forgot-password")
                || path.startsWith("/auth/reset-password")
                || path.startsWith("/chatbot/ask");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI().replaceFirst("^/api", "");

        if (isRateLimited(path)) {
            if (buckets.size() > MAX_TRACKED_IPS) {
                buckets.clear(); // crude memory-DoS guard; legitimate users just get a fresh bucket
            }
            Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), ip -> newBucket());

            if (!bucket.tryConsume(1)) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Too many attempts. Please try again in a minute.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
