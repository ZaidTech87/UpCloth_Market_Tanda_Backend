package com.clothmarket.service;

import com.clothmarket.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UploadValidatorTest {

    private static final long MB = 1024 * 1024;

    private static byte[] jpeg() {
        byte[] b = new byte[16];
        b[0] = (byte) 0xFF; b[1] = (byte) 0xD8; b[2] = (byte) 0xFF;
        return b;
    }

    private static byte[] png() {
        byte[] b = new byte[16];
        b[0] = (byte) 0x89; b[1] = 'P'; b[2] = 'N'; b[3] = 'G';
        return b;
    }

    private static UploadValidator.Checked check(MediaKind k, String name, String type, long size, byte[] head) {
        return UploadValidator.check(k, name, type, size, head, 10 * MB, 50 * MB, 10 * MB);
    }

    private static int statusOf(Runnable r) {
        try {
            r.run();
            return -1;
        } catch (ApiException e) {
            return e.getStatus().value();
        }
    }

    @Test
    void acceptsValidImageVideoAndAudio() {
        assertEquals("jpg", check(MediaKind.IMAGE, "me.JPG", "image/jpeg", 1000, jpeg()).extension());
        assertEquals("png", check(MediaKind.IMAGE, null, "image/png", 1000, png()).extension());
        assertEquals("video", check(MediaKind.POST_MEDIA, "c.mp4", "video/mp4", 5 * MB, new byte[16]).family());
        assertEquals("m4a", check(MediaKind.AUDIO, "voice-message.m4a", "audio/m4a", 5000, new byte[16]).extension());
        assertEquals("webm", check(MediaKind.AUDIO, "b.webm", "audio/webm;codecs=opus", 5000, new byte[16]).extension());
    }

    @Test
    void rejectsWrongTypesFakeImagesAndOversize() {
        assertEquals(400, statusOf(() -> check(MediaKind.IMAGE, "a.exe", "application/x-msdownload", 100, jpeg())));
        assertEquals(400, statusOf(() -> check(MediaKind.IMAGE, "a.jpg", "image/jpeg", 100, new byte[16])));
        assertEquals(400, statusOf(() -> check(MediaKind.IMAGE, "a.jpg", "video/mp4", 100, jpeg())));
        assertEquals(400, statusOf(() -> check(MediaKind.AUDIO, "a.mp4", "image/png", 100, png())));
        assertEquals(413, statusOf(() -> check(MediaKind.IMAGE, "a.jpg", "image/jpeg", 11 * MB, jpeg())));
        assertEquals(400, statusOf(() -> check(MediaKind.IMAGE, "a.jpg", "image/jpeg", 0, jpeg())));
        // picture disguised as a video would dodge image moderation
        assertEquals(400, statusOf(() -> check(MediaKind.POST_MEDIA, "a.mp4", "video/mp4", 100, png())));
    }

    @Test
    void neverUsesAttackerControlledExtension() {
        assertEquals("jpg",
                check(MediaKind.IMAGE, "x./../../etc/passwd", "image/jpeg", 100, jpeg()).extension());
        assertNull(UploadValidator.safeExtension("evil.jp/g"));
        assertNull(UploadValidator.safeExtension("a.verylongext"));
        assertEquals("png", UploadValidator.safeExtension("a.PNG"));
    }
}
