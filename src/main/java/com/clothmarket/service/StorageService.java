package com.clothmarket.service;

import com.clothmarket.config.R2Properties;
import com.clothmarket.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Media storage on a PRIVATE Cloudflare R2 bucket (S3-compatible API).
 *
 * The database stores permanent object keys such as "posts/<uuid>.jpg".
 * Signed, short-lived GET URLs are generated only when a response is written
 * (see getSignedUrl) and are never stored.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    /** Only these folder names are ever used as the first segment of an object key. */
    private static final Set<String> FOLDERS = Set.of("posts", "profiles", "voice");

    /** Exact shape of every key this service generates: folder/<uuid>.<ext>. */
    private static final Pattern OBJECT_KEY =
            Pattern.compile("^(posts|profiles|voice)/[A-Za-z0-9-]{1,64}\\.[a-z0-9]{2,5}$");

    /** AWS Signature V4 presigned URLs cannot be valid for longer than 7 days. */
    private static final long MAX_SIGNED_URL_SECONDS = 604_800L;

    private static final String CACHE_CONTROL = "private, max-age=86400";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final R2Properties r2Properties;

    private final long maxImageBytes;
    private final long maxVideoBytes;
    private final long maxAudioBytes;

    public StorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            R2Properties r2Properties,
            @Value("${upload.max-image-bytes:10485760}") long maxImageBytes,
            @Value("${upload.max-video-bytes:52428800}") long maxVideoBytes,
            @Value("${upload.max-audio-bytes:10485760}") long maxAudioBytes) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.r2Properties = r2Properties;
        this.maxImageBytes = maxImageBytes;
        this.maxVideoBytes = maxVideoBytes;
        this.maxAudioBytes = maxAudioBytes;
    }

    /**
     * Validates the upload (type, extension, size, image signature) and stores it in R2.
     * Returns the PERMANENT OBJECT KEY (not a URL). Throws 503 if R2 is unavailable.
     */
    public String store(MultipartFile file, String folder, MediaKind kind) throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No file received");
        }
        if (!FOLDERS.contains(folder)) {
            throw new IllegalArgumentException("Unknown upload folder");
        }

        byte[] head = readHead(file, 16);
        UploadValidator.Checked checked = UploadValidator.check(
                kind, file.getOriginalFilename(), file.getContentType(), file.getSize(), head,
                maxImageBytes, maxVideoBytes, maxAudioBytes);

        String objectKey = folder + "/" + UUID.randomUUID() + "." + checked.extension();
        // Content-Type comes from the VALIDATED extension, never from the client header.
        String contentType = contentTypeFor(checked.family(), checked.extension());

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(r2Properties.getBucketName())
                .key(objectKey)
                .contentType(contentType)
                .cacheControl(CACHE_CONTROL)
                .build();

        InputStream in = file.getInputStream();
        try {
            s3Client.putObject(request, RequestBody.fromInputStream(in, file.getSize()));
            return objectKey;
        } catch (RuntimeException e) {
            log.error("R2 upload failed (key={}, cause={})", objectKey, describe(e));
            throw storageUnavailable();
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // The upload outcome is already decided; a close failure must not orphan the object.
            }
        }
    }

    /**
     * Generates a temporary signed GET URL for a stored object key. The URL is never
     * persisted. Voice objects use the shorter voice TTL.
     */
    public String getSignedUrl(String objectKey) {
        if (!isValidObjectKey(objectKey)) {
            throw ApiException.badRequest("Invalid media object key");
        }

        long ttlSeconds = objectKey.startsWith("voice/")
                ? r2Properties.getSignedUrlTtlVoiceSeconds()
                : r2Properties.getSignedUrlTtlMediaSeconds();

        if (ttlSeconds <= 0 || ttlSeconds > MAX_SIGNED_URL_SECONDS) {
            log.error("Signed URL TTL is outside the allowed range (1..{} seconds)", MAX_SIGNED_URL_SECONDS);
            throw storageUnavailable();
        }

        try {
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(ttlSeconds))
                    .getObjectRequest(get -> get
                            .bucket(r2Properties.getBucketName())
                            .key(objectKey))
                    .build();
            return s3Presigner.presignGetObject(presignRequest).url().toExternalForm();
        } catch (RuntimeException e) {
            // Never log the URL itself.
            log.error("Could not generate a signed URL (key={}, cause={})", objectKey, describe(e));
            throw storageUnavailable();
        }
    }

    /**
     * Deletes an R2 object by its permanent key. Legacy "https://" and "/uploads/..." values
     * and anything that is not one of our own keys are ignored.
     * Throws 503 if the R2 call itself fails (callers decide whether that matters).
     */
    public void deleteMedia(String mediaKey) throws IOException {
        if (mediaKey == null || mediaKey.isBlank()) {
            return;
        }
        String value = mediaKey.trim();
        if (!isValidObjectKey(value)) {
            // Includes legacy URLs and local "/uploads/..." paths: nothing of ours to delete.
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(r2Properties.getBucketName())
                    .key(value)
                    .build());
        } catch (RuntimeException e) {
            log.error("R2 delete failed (key={}, cause={})", value, describe(e));
            throw storageUnavailable();
        }
    }

    /**
     * Best-effort delete that NEVER throws. Use it for cleanup after a database
     * operation, where a storage failure must not change the API response.
     */
    public void deleteQuietly(String mediaKey) {
        try {
            deleteMedia(mediaKey);
        } catch (Exception e) {
            log.warn("Media cleanup failed; object may remain in storage (key={})", mediaKey);
        }
    }

    /** True only for keys of the exact form folder/<id>.<ext> with an allowed folder. */
    public boolean isValidObjectKey(String objectKey) {
        return objectKey != null && OBJECT_KEY.matcher(objectKey).matches();
    }

    private static String contentTypeFor(String family, String ext) {
        boolean audio = "audio".equals(family);
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "heic" -> "image/heic";
            case "heif" -> "image/heif";
            case "mov" -> "video/quicktime";
            case "m4v" -> "video/x-m4v";
            case "mp4" -> audio ? "audio/mp4" : "video/mp4";
            case "webm" -> audio ? "audio/webm" : "video/webm";
            case "3gp" -> audio ? "audio/3gpp" : "video/3gpp";
            case "m4a" -> "audio/mp4";
            case "mp3" -> "audio/mpeg";
            case "aac" -> "audio/aac";
            case "ogg", "oga" -> "audio/ogg";
            case "opus" -> "audio/opus";
            case "wav" -> "audio/wav";
            case "caf" -> "audio/x-caf";
            case "amr" -> "audio/amr";
            default -> "application/octet-stream";
        };
    }

    /** Safe description for logs: class, HTTP status and S3 error code only (no message text). */
    private static String describe(RuntimeException e) {
        if (e instanceof AwsServiceException aws) {
            String code = aws.awsErrorDetails() != null ? aws.awsErrorDetails().errorCode() : "n/a";
            return aws.getClass().getSimpleName() + " status=" + aws.statusCode() + " code=" + code;
        }
        return e.getClass().getSimpleName();
    }

    private static ApiException storageUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Media storage is temporarily unavailable");
    }

    private static byte[] readHead(MultipartFile file, int n) throws IOException {
        try (InputStream in = file.getInputStream()) {
            byte[] buf = in.readNBytes(n);
            return buf.length == n ? buf : Arrays.copyOf(buf, buf.length);
        }
    }
}