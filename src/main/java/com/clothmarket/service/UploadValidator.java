package com.clothmarket.service;

import com.clothmarket.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure (framework-free) upload checks, so they are easy to unit test.
 * Decides whether an upload is acceptable and which SAFE file extension to
 * store it under. The original filename is never used as a path.
 */
public final class UploadValidator {

    private UploadValidator() {}

    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif", "heic", "heif");
    private static final Set<String> VIDEO_EXT = Set.of("mp4", "mov", "webm", "3gp", "m4v");
    private static final Set<String> AUDIO_EXT = Set.of(
            "webm", "m4a", "mp3", "aac", "ogg", "oga", "opus", "wav", "3gp", "mp4", "caf", "amr");

    private static final Map<String, String> TYPE_TO_EXT = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"), Map.entry("image/jpg", "jpg"), Map.entry("image/png", "png"),
            Map.entry("image/webp", "webp"), Map.entry("image/gif", "gif"),
            Map.entry("image/heic", "heic"), Map.entry("image/heif", "heif"),
            Map.entry("video/mp4", "mp4"), Map.entry("video/quicktime", "mov"),
            Map.entry("video/webm", "webm"), Map.entry("video/3gpp", "3gp"), Map.entry("video/x-m4v", "m4v"),
            Map.entry("audio/webm", "webm"), Map.entry("audio/mp4", "m4a"), Map.entry("audio/m4a", "m4a"),
            Map.entry("audio/x-m4a", "m4a"), Map.entry("audio/mpeg", "mp3"), Map.entry("audio/aac", "aac"),
            Map.entry("audio/ogg", "ogg"), Map.entry("audio/opus", "opus"), Map.entry("audio/wav", "wav"),
            Map.entry("audio/x-wav", "wav"), Map.entry("audio/3gpp", "3gp"), Map.entry("audio/amr", "amr"),
            Map.entry("audio/x-caf", "caf"));

    /** What a validated upload is: its media family and the extension to store it under. */
    public record Checked(String family, String extension) {}

    public static Checked check(MediaKind kind, String originalFilename, String contentType,
                                long sizeBytes, byte[] head,
                                long maxImageBytes, long maxVideoBytes, long maxAudioBytes) {
        if (sizeBytes <= 0) {
            throw ApiException.badRequest("File is empty");
        }

        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        int semi = type.indexOf(';');
        if (semi >= 0) {
            type = type.substring(0, semi).trim();
        }

        String ext = safeExtension(originalFilename);
        String family = familyFor(kind, type);

        if (family == null) {
            throw ApiException.badRequest("This file type is not allowed");
        }

        Set<String> allowedExt = switch (family) {
            case "image" -> IMAGE_EXT;
            case "video" -> VIDEO_EXT;
            default -> AUDIO_EXT;
        };

        if (ext == null || !allowedExt.contains(ext)) {
            // Missing/odd filename (some pickers omit it): derive from the declared type.
            String fromType = TYPE_TO_EXT.get(type);
            ext = fromType != null && allowedExt.contains(fromType) ? fromType : null;
        }
        if (ext == null) {
            throw ApiException.badRequest("This file type is not allowed");
        }

        long max = switch (family) {
            case "image" -> maxImageBytes;
            case "video" -> maxVideoBytes;
            default -> maxAudioBytes;
        };
        if (sizeBytes > max) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "File is too large (max " + (max / (1024 * 1024)) + " MB)");
        }

        if ("image".equals(family) && !looksLikeImage(head)) {
            throw ApiException.badRequest("File is not a valid image");
        }
        // A real picture uploaded as "video"/"audio" would dodge image moderation.
        if (!"image".equals(family) && looksLikeRasterImage(head)) {
            throw ApiException.badRequest("File content does not match its declared type");
        }

        return new Checked(family, ext);
    }

    private static String familyFor(MediaKind kind, String type) {
        switch (kind) {
            case IMAGE:
                return isImageType(type) ? "image" : null;
            case POST_MEDIA:
                if (isImageType(type)) return "image";
                if (type.startsWith("video/")) return "video";
                return null;
            case AUDIO:
                if (type.startsWith("audio/") || type.equals("video/webm") || type.equals("video/mp4")) {
                    return "audio";
                }
                return null;
            default:
                return null;
        }
    }

    private static boolean isImageType(String type) {
        return type.equals("image/jpeg") || type.equals("image/jpg") || type.equals("image/png")
                || type.equals("image/webp") || type.equals("image/gif")
                || type.equals("image/heic") || type.equals("image/heif");
    }

    /** Lower-cased extension if it is 1-5 plain alphanumerics, otherwise null (blocks "../" tricks). */
    static String safeExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (ext.length() > 5) {
            return null;
        }
        for (int i = 0; i < ext.length(); i++) {
            char c = ext.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (!ok) {
                return null;
            }
        }
        return ext;
    }

    /** JPEG/PNG/GIF/WebP signatures only (HEIF shares the "ftyp" box with MP4, so it is excluded). */
    static boolean looksLikeRasterImage(byte[] h) {
        if (h == null || h.length < 12) {
            return false;
        }
        boolean jpeg = (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
        boolean png = (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
        boolean gif = h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8';
        boolean webp = h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
        return jpeg || png || gif || webp;
    }

    /** Magic-number check for the image formats we accept. */
    static boolean looksLikeImage(byte[] h) {
        if (h == null || h.length < 12) {
            return false;
        }
        boolean jpeg = (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
        boolean png = (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
        boolean gif = h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8';
        boolean webp = h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
        boolean heif = h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p';
        return jpeg || png || gif || webp || heif;
    }
}
