package com.clothmarket.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Sends OTP emails through the Resend REST API (https://resend.com/docs/api-reference/emails/send-email).
 *
 * Configuration (environment variables, never hard-coded):
 *   RESEND_API_KEY  -> resend.api-key
 *   MAIL_FROM       -> mail.from   e.g.  ClothMarket <no-reply@your-verified-domain.com>
 *
 * The sender domain must be verified in your Resend account, otherwise Resend only delivers
 * to the account owner's own address.
 *
 * Neither the API key nor the OTP is ever written to the log by this class.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String apiUrl;
    private final String from;
    private final long ttlMinutes;

    @Autowired
    public EmailService(@Value("${resend.api-key:}") String apiKey,
                        @Value("${resend.api-url:https://api.resend.com/emails}") String apiUrl,
                        @Value("${mail.from:}") String from,
                        @Value("${otp.ttl-seconds:300}") long otpTtlSeconds) {
        this(buildRestTemplate(), apiKey, apiUrl, from, otpTtlSeconds);
    }

    /** Test constructor with an injectable RestTemplate. */
    EmailService(RestTemplate restTemplate, String apiKey, String apiUrl, String from, long otpTtlSeconds) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiUrl = apiUrl;
        this.from = from == null ? "" : from.trim();
        this.ttlMinutes = Math.max(1, (otpTtlSeconds + 59) / 60);
    }

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }

    /** True when both the API key and the sender address are set. */
    public boolean isConfigured() {
        return !apiKey.isEmpty() && !from.isEmpty();
    }

    /**
     * Emails the OTP. Throws if the email could not be handed to Resend; the caller then
     * discards the OTP and tells the user to try again.
     */
    public void sendOtp(String toEmail, String otp, OtpService.Purpose purpose) {
        if (!isConfigured()) {
            throw new IllegalStateException("Email sending is not configured");
        }

        String subject;
        String intro;
        switch (purpose) {
            case PASSWORD_RESET -> {
                subject = "Your ClothMarket password reset code";
                intro = "Use this code to reset your ClothMarket password.";
            }
            default -> {
                subject = "Your ClothMarket verification code";
                intro = "Use this code to verify your email and finish creating your ClothMarket account.";
            }
        }
        String validity = "It is valid for " + ttlMinutes + (ttlMinutes == 1 ? " minute" : " minutes")
                + " and can be used only once.";
        String ignore = "If you did not request this, you can ignore this email. Never share this code with anyone.";

        String text = intro + "\n\nYour code: " + otp + "\n\n" + validity + "\n" + ignore + "\n";
        String html = "<div style=\"font-family:system-ui,Arial,sans-serif;font-size:16px;color:#1a1a1a\">"
                + "<p>" + intro + "</p>"
                + "<p style=\"font-size:28px;font-weight:700;letter-spacing:6px;margin:16px 0\">" + otp + "</p>"
                + "<p>" + validity + "</p>"
                + "<p style=\"color:#666\">" + ignore + "</p>"
                + "</div>";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set(HttpHeaders.USER_AGENT, "ClothMarket-Backend/1.0");

        Map<String, Object> body = Map.of(
                "from", from,
                "to", List.of(toEmail),
                "subject", subject,
                "text", text,
                "html", html
        );

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(apiUrl, new HttpEntity<>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Email provider returned HTTP " + response.getStatusCode().value());
            }
        } catch (RestClientException e) {
            // Class name only: the exception message can echo the request, which contains the OTP.
            log.error("Resend request failed: {}", e.getClass().getSimpleName());
            throw new IllegalStateException("Email provider request failed");
        }
    }
}