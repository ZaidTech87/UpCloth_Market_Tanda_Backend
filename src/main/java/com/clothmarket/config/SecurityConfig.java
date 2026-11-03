package com.clothmarket.config;

import com.clothmarket.repository.UserRepository;
import com.clothmarket.security.JwtAuthenticationFilter;
import com.clothmarket.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Value("${allowed.origins}")
    private String allowedOrigins;

    private JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil, userRepository);
    }

    /**
     * Stop Spring Boot from also registering the JWT filter as a plain servlet
     * filter; it is added to the Spring Security chain below.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> disableAutoRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter());
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** CORS for the web frontend; origins come from ALLOWED_ORIGINS. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Stateless JWT API: no cookies/sessions, so CSRF does not apply.
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public authentication + OTP endpoints
                        .requestMatchers(HttpMethod.POST,
                                "/auth/signup",
                                "/auth/signup/send-otp",
                                "/auth/login",
                                "/auth/forgot-password",
                                "/auth/reset-password").permitAll()

                        // Public legal pages (privacy policy, terms, account deletion info)
                        .requestMatchers(HttpMethod.GET, "/legal/**").permitAll()

                        // Liveness probe for Render / load balancers
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()

                        // Media is served straight from the private R2 bucket through short-lived
                        // signed URLs, so no media route is exposed by this backend.

                        // WebSocket HANDSHAKES. Browsers/SockJS and React Native cannot attach an
                        // Authorization header to the HTTP upgrade, so authentication is enforced
                        // on the STOMP CONNECT frame instead (StompAuthChannelInterceptor).
                        .requestMatchers("/ws/**", "/ws-native/**").permitAll()

                        // Everything else requires a valid JWT
                        .anyRequest().authenticated()
                )

                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}