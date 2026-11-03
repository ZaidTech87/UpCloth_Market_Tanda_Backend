package com.clothmarket.service;

import com.clothmarket.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Checks post images for nudity (Sightengine) before they are stored.
 * Unsafe content throws, so the post is never saved.
 *
 * Behaviour when moderation is NOT configured or the provider is unreachable is
 * controlled by moderation.fail-closed (MODERATION_FAIL_CLOSED):
 *   false (default) - allow the upload, log a warning (previous behaviour)
 *   true            - reject the upload
 * Only images are scanned; videos are not (see PENDING_WORK.md).
 */
@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    @Value("${sightengine.api.user:}")
    private String apiUser;

    @Value("${sightengine.api.secret:}")
    private String apiSecret;

    @Value("${sightengine.api.url}")
    private String apiUrl;

    @Value("${moderation.fail-closed:false}")
    private boolean failClosed;

    private final RestTemplate restTemplate = buildRestTemplate();

    // Score at/above which content is blocked (0 = safe, 1 = definitely unsafe)
    private static final double NUDITY_THRESHOLD = 0.5;

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        return new RestTemplate(factory);
    }

    public void checkImage(MultipartFile file) {
        if (apiUser == null || apiUser.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            unavailable("Moderation is not configured");
            return;
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (java.io.IOException e) {
            unavailable("Could not read the file for moderation");
            return;
        }

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("media", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return "upload";
            }
        });
        form.add("models", "nudity-2.1");
        form.add("api_user", apiUser);
        form.add("api_secret", apiSecret);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        JsonNode response;
        try {
            response = restTemplate.postForObject(apiUrl, new HttpEntity<>(form, headers), JsonNode.class);
        } catch (Exception e) {
            unavailable("Moderation service error: " + e.getClass().getSimpleName());
            return;
        }

        if (response == null || !response.has("nudity")) {
            unavailable("Moderation service returned an unexpected response");
            return;
        }

        JsonNode nudity = response.get("nudity");
        double sexualActivity = nudity.path("sexual_activity").asDouble(0);
        double sexualDisplay = nudity.path("sexual_display").asDouble(0);
        double erotica = nudity.path("erotica").asDouble(0);

        double maxScore = Math.max(sexualActivity, Math.max(sexualDisplay, erotica));

        if (maxScore >= NUDITY_THRESHOLD) {
            throw ApiException.badRequest(
                    "This image does not follow the community guidelines and cannot be uploaded.");
        }
    }

    private void unavailable(String reason) {
        if (failClosed) {
            log.warn("{} - rejecting upload (moderation.fail-closed=true)", reason);
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Image check is temporarily unavailable. Please try again later.");
        }
        log.warn("{} - allowing upload (moderation.fail-closed=false)", reason);
    }
}
