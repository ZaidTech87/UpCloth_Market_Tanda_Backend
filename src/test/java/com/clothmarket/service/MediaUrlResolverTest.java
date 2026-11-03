
package com.clothmarket.service;

import com.clothmarket.config.R2Properties;
import com.clothmarket.exception.ApiException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaUrlResolverTest {

    @Mock
    private StorageService storageService;

    @Mock
    private R2Properties r2Properties;

    private MediaUrlResolver resolver;

    @BeforeEach
    void setUp() {
        // A mocked R2Properties returns 0 for every getter, which would mean "expires
        // immediately" and disable caching. Use the real defaults instead.
        lenient().when(r2Properties.getSignedUrlTtlMediaSeconds()).thenReturn(21600L);
        lenient().when(r2Properties.getSignedUrlTtlVoiceSeconds()).thenReturn(3600L);

        resolver = new MediaUrlResolver(
                storageService,
                r2Properties
        );
    }

    @Test
    void shouldReturnNullForNullValue() {

        assertEquals(null, resolver.resolve(null));

        verifyNoInteractions(storageService);
    }

    @Test
    void shouldReturnBlankValueUnchanged() {

        assertEquals("", resolver.resolve(""));

        verifyNoInteractions(storageService);
    }

    @Test
    void shouldPreserveHttpsUrl() {

        String url = "https://example.com/image.jpg";

        assertEquals(url, resolver.resolve(url));

        verifyNoInteractions(storageService);
    }

    @Test
    void shouldPreserveHttpUrl() {

        String url = "http://example.com/image.jpg";

        assertEquals(url, resolver.resolve(url));

        verifyNoInteractions(storageService);
    }

    @Test
    void shouldPreserveLegacyUploadPath() {

        String path = "/uploads/posts/image.jpg";

        assertEquals(path, resolver.resolve(path));

        verifyNoInteractions(storageService);
    }

    @Test
    void shouldResolvePostObjectKey() {

        String key = "posts/test-image.jpg";
        String signedUrl = "https://signed-r2-url/post-image";

        when(storageService.getSignedUrl(key))
                .thenReturn(signedUrl);

        String result = resolver.resolve(key);

        assertEquals(signedUrl, result);

        verify(storageService, times(1))
                .getSignedUrl(key);
    }

    @Test
    void shouldResolveProfileObjectKey() {

        String key = "profiles/test-profile.jpg";
        String signedUrl = "https://signed-r2-url/profile-image";

        when(storageService.getSignedUrl(key))
                .thenReturn(signedUrl);

        assertEquals(signedUrl, resolver.resolve(key));

        verify(storageService).getSignedUrl(key);
    }

    @Test
    void shouldResolveVoiceObjectKey() {

        String key = "voice/test-voice.mp3";
        String signedUrl = "https://signed-r2-url/voice";

        when(storageService.getSignedUrl(key))
                .thenReturn(signedUrl);

        assertEquals(signedUrl, resolver.resolve(key));

        verify(storageService).getSignedUrl(key);
    }

    @Test
    void shouldReuseCachedSignedUrl() {

        String key = "posts/cached-image.jpg";
        String signedUrl = "https://signed-r2-url/cached-image";

        when(storageService.getSignedUrl(key))
                .thenReturn(signedUrl);

        String first = resolver.resolve(key);
        String second = resolver.resolve(key);

        assertEquals(signedUrl, first);
        assertEquals(signedUrl, second);

        verify(storageService, times(1))
                .getSignedUrl(key);
    }

    @Test
    void shouldGenerateFreshUrlAfterEviction() {

        String key = "posts/evicted-image.jpg";

        when(storageService.getSignedUrl(key))
                .thenReturn(
                        "https://signed-r2-url/first",
                        "https://signed-r2-url/second"
                );

        String first = resolver.resolve(key);

        resolver.evict(key);

        String second = resolver.resolve(key);

        assertEquals("https://signed-r2-url/first", first);
        assertEquals("https://signed-r2-url/second", second);

        verify(storageService, times(2))
                .getSignedUrl(key);
    }

    @Test
    void shouldClearEntireCache() {

        String key = "posts/cache-clear.jpg";

        when(storageService.getSignedUrl(key))
                .thenReturn(
                        "https://signed-r2-url/first",
                        "https://signed-r2-url/second"
                );

        resolver.resolve(key);

        resolver.clearCache();

        resolver.resolve(key);

        verify(storageService, times(2))
                .getSignedUrl(key);
    }

    // ---------- Added: behaviour required by the private-bucket design ----------

    @Test
    void shouldNeverCacheVoiceUrls() {

        String key = "voice/private-voice.m4a";

        when(storageService.getSignedUrl(key))
                .thenReturn(
                        "https://signed-r2-url/voice-1",
                        "https://signed-r2-url/voice-2"
                );

        assertEquals("https://signed-r2-url/voice-1", resolver.resolve(key));
        assertEquals("https://signed-r2-url/voice-2", resolver.resolve(key));

        verify(storageService, times(2))
                .getSignedUrl(key);
    }

    @Test
    void shouldReturnNullInsteadOfThrowingWhenSigningFails() {

        String key = "posts/test-image.jpg";

        when(storageService.getSignedUrl(key))
                .thenThrow(new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Media storage is temporarily unavailable"));

        assertNull(resolver.resolve(key));
    }

    @Test
    void shouldReturnNullForInvalidKey() {

        String key = "../outside/image.jpg";

        when(storageService.getSignedUrl(key))
                .thenThrow(ApiException.badRequest("Invalid media object key"));

        assertNull(resolver.resolve(key));
    }
}