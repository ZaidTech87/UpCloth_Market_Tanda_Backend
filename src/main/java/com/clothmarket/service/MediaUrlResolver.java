package com.clothmarket.service;

import com.clothmarket.config.R2Properties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Resolves a stored media reference into something a client can load.
 *
 * The database holds permanent object keys such as "posts/<uuid>.jpg". At the moment a response
 * is written this class turns a key into a short-lived signed GET URL. Signed URLs are never
 * persisted. Legacy absolute "http(s)://" values and "/uploads/..." paths pass through unchanged.
 *
 * resolve() never throws: if a URL cannot be produced (including an invalid key, which
 * StorageService.getSignedUrl rejects) that single field is returned as null, so one bad
 * reference cannot break a whole feed, chat history or WebSocket push.
 */
@Service
public class MediaUrlResolver {

    private static final Logger log = LoggerFactory.getLogger(MediaUrlResolver.class);

    private static final int MAX_CACHE_ENTRIES = 10_000;

    /** Jackson creates serializers itself, so they reach this bean through a static holder. */
    private static volatile MediaUrlResolver instance;

    private final StorageService storageService;
    private final R2Properties r2Properties;

    /** key -> cached signed URL (post/profile media only; voice is never cached). */
    private final ConcurrentMap<String, CachedUrl> cache = new ConcurrentHashMap<>();

    public MediaUrlResolver(
            StorageService storageService,
            R2Properties r2Properties
    ) {
        this.storageService = storageService;
        this.r2Properties = r2Properties;
    }

    @PostConstruct
    public void register() {
        instance = this;
    }

    /** Null before the Spring context has started (for example in plain unit tests). */
    static MediaUrlResolver getInstance() {
        return instance;
    }

    /**
     * Converts a stored R2 object key into a temporary signed URL.
     *
     * Legacy absolute URLs and "/uploads/..." paths are returned unchanged.
     * Null and blank values remain unchanged.
     * Returns null (never throws) when a URL cannot be produced.
     */
    public String resolve(String storedValue) {

        if (storedValue == null || storedValue.isBlank()) {
            return storedValue;
        }

        String value = storedValue.trim();

        // Preserve existing absolute media URLs and legacy local references.
        if (value.startsWith("https://")
                || value.startsWith("http://")
                || value.startsWith("/uploads/")) {
            return value;
        }

        try {
            if (value.startsWith("voice/")) {
                // Private voice: always a fresh URL so it keeps its full short TTL.
                return storageService.getSignedUrl(value);
            }
            return resolveMedia(value);
        } catch (RuntimeException e) {
            // Never log the URL; only the exception class is safe to record.
            log.warn("Could not sign a media URL (cause={})", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Removes a cached URL after media replacement or deletion.
     */
    public void evict(String objectKey) {

        if (objectKey != null && !objectKey.isBlank()) {
            cache.remove(objectKey);
        }
    }

    /**
     * Clears all cached signed URLs.
     */
    public void clearCache() {
        cache.clear();
    }

    private String resolveMedia(String key) {

        long now = System.currentTimeMillis();

        CachedUrl cached = cache.get(key);
        if (cached != null && now < cached.reuseUntilMillis()) {
            return cached.url();
        }

        String signedUrl = storageService.getSignedUrl(key);

        long ttlMillis = r2Properties.getSignedUrlTtlMediaSeconds() * 1000L;
        // Stop reusing a URL once only a sixth of its lifetime is left (at least 60 seconds),
        // so every URL handed to a client still has plenty of validity.
        long margin = Math.max(60_000L, ttlMillis / 6);
        long reuseUntil = now + ttlMillis - margin;

        if (reuseUntil > now) {
            evictIfLarge(now);
            cache.put(key, new CachedUrl(signedUrl, reuseUntil));
        }

        return signedUrl;
    }

    private void evictIfLarge(long now) {
        if (cache.size() < MAX_CACHE_ENTRIES) {
            return;
        }
        cache.values().removeIf(c -> c.reuseUntilMillis() <= now);
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
    }

    private record CachedUrl(
            String url,
            long reuseUntilMillis
    ) {
    }
}