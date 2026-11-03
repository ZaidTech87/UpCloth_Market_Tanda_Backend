package com.clothmarket.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class Fast2SmsService {

    private static final Logger log = LoggerFactory.getLogger(Fast2SmsService.class);

    @Value("${fast2sms.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Sends the OTP SMS. Throws if the provider is not configured or rejects the request. */
    public void sendOtp(String mobile, String otp) {
        if (!isConfigured()) {
            throw new IllegalStateException("SMS provider is not configured (FAST2SMS_API_KEY)");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        Map<String, Object> body = new HashMap<>();
        body.put("route", "q");
        body.put("message", "Your ClothMarket OTP is " + otp
                + ". Do not share this OTP with anyone.");
        body.put("numbers", mobile);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "https://www.fast2sms.com/dev/bulkV2",
                new HttpEntity<>(body, headers),
                String.class);

        // Never log the response body verbatim: it can echo the message (the OTP).
        log.info("Fast2SMS responded with HTTP {}", response.getStatusCode().value());
    }
}
