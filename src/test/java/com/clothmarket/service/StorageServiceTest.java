package com.clothmarket.service;

import com.clothmarket.config.R2Properties;
import com.clothmarket.exception.ApiException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private R2Properties properties;

    private StorageService storageService;

    @BeforeEach
    void setUp() {

        properties = new R2Properties();

        properties.setAccountId("test-account");
        properties.setAccessKeyId("test-access-key");
        properties.setSecretAccessKey("test-secret-key");
        properties.setBucketName("clothmarket-media-dev");

        storageService = new StorageService(
                s3Client,
                s3Presigner,
                properties,
                10L * 1024 * 1024,
                50L * 1024 * 1024,
                10L * 1024 * 1024
        );
    }

    @Test
    void deleteMediaShouldIgnoreNullKey() {

        assertDoesNotThrow(() ->
                storageService.deleteMedia(null)
        );

        verifyNoInteractions(s3Client);
    }

    @Test
    void deleteMediaShouldIgnoreBlankKey() {

        assertDoesNotThrow(() ->
                storageService.deleteMedia(" ")
        );

        verifyNoInteractions(s3Client);
    }

    @Test
    void deleteMediaShouldIgnoreLegacyHttpUrl() {

        assertDoesNotThrow(() ->
                storageService.deleteMedia(
                        "https://example.com/media/image.jpg"
                )
        );

        verifyNoInteractions(s3Client);
    }

    @Test
    void deleteMediaShouldIgnoreLegacyLocalUploadPath() {

        assertDoesNotThrow(() ->
                storageService.deleteMedia(
                        "/uploads/posts/image.jpg"
                )
        );

        verifyNoInteractions(s3Client);
    }

    @Test
    void deleteMediaShouldIgnoreInvalidObjectKey() {

        assertDoesNotThrow(() ->
                storageService.deleteMedia(
                        "../outside/image.jpg"
                )
        );

        verifyNoInteractions(s3Client);
    }

    @Test
    void deleteMediaShouldDeleteValidPostObject() throws Exception {

        storageService.deleteMedia("posts/test-image.jpg");

        verify(s3Client).deleteObject(
                any(DeleteObjectRequest.class)
        );
    }

    @Test
    void deleteMediaShouldDeleteValidProfileObject() throws Exception {

        storageService.deleteMedia("profiles/test-profile.jpg");

        verify(s3Client).deleteObject(
                any(DeleteObjectRequest.class)
        );
    }

    @Test
    void deleteMediaShouldDeleteValidVoiceObject() throws Exception {

        storageService.deleteMedia("voice/test-voice.mp3");

        verify(s3Client).deleteObject(
                any(DeleteObjectRequest.class)
        );
    }

    @Test
    void deleteMediaShouldNotDeleteInvalidFolder() throws Exception {

        storageService.deleteMedia("unknown/test-file.jpg");

        verify(s3Client, never()).deleteObject(
                any(DeleteObjectRequest.class)
        );
    }

    // ---------- Added: behaviour introduced by the R2 migration ----------

    @Test
    void deleteMediaShouldReturn503WhenR2Fails() {

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("simulated R2 outage"));

        ApiException e = assertThrows(ApiException.class, () ->
                storageService.deleteMedia("posts/test-image.jpg")
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
    }

    @Test
    void deleteQuietlyShouldNeverThrowWhenR2Fails() {

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("simulated R2 outage"));

        assertDoesNotThrow(() ->
                storageService.deleteQuietly("posts/test-image.jpg")
        );
    }

    @Test
    void getSignedUrlShouldRejectInvalidKeyWithoutSigning() {

        ApiException e = assertThrows(ApiException.class, () ->
                storageService.getSignedUrl("../outside/image.jpg")
        );

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        verifyNoInteractions(s3Presigner);
    }

    @Test
    void isValidObjectKeyShouldAcceptOnlyOurKeyShape() {

        assertTrue(storageService.isValidObjectKey("posts/abc-123.jpg"));
        assertTrue(storageService.isValidObjectKey("profiles/abc-123.png"));
        assertTrue(storageService.isValidObjectKey("voice/abc-123.m4a"));
        assertTrue(storageService.isValidObjectKey("posts/abc-123.jpeg"));

        assertFalse(storageService.isValidObjectKey(null));
        assertFalse(storageService.isValidObjectKey(""));
        assertFalse(storageService.isValidObjectKey("posts/abc-123"));
        assertFalse(storageService.isValidObjectKey("posts/abc-123.JPG"));
        assertFalse(storageService.isValidObjectKey("posts/sub/abc-123.jpg"));
        assertFalse(storageService.isValidObjectKey("posts/.jpg"));
        assertFalse(storageService.isValidObjectKey("other/abc-123.jpg"));
        assertFalse(storageService.isValidObjectKey("/posts/abc-123.jpg"));
        assertFalse(storageService.isValidObjectKey("https://example.com/a.jpg"));
    }
}