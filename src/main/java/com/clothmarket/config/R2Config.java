package com.clothmarket.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

/**
 * Cloudflare R2 (S3-compatible) client and presigner.
 * The endpoint is https://<account-id>.r2.cloudflarestorage.com and NEVER contains the bucket name.
 */
@Configuration
@EnableConfigurationProperties(R2Properties.class)
public class R2Config {

    private final R2Properties properties;

    public R2Config(R2Properties properties) {
        this.properties = properties;
    }

    @Bean
    public S3Client r2S3Client() {

        properties.validate();

        return S3Client.builder()
                .endpointOverride(getEndpoint())
                .region(Region.of("auto"))
                .credentialsProvider(getCredentialsProvider())
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .chunkedEncodingEnabled(false)
                                .build()
                )
                // AWS SDK 2.30+ adds default checksums that S3-compatible stores may reject.
                // Only send/validate them when the operation requires it.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                // Guards against a stalled transfer holding a request thread forever.
                .overrideConfiguration(
                        ClientOverrideConfiguration.builder()
                                .apiCallTimeout(Duration.ofSeconds(180))
                                .apiCallAttemptTimeout(Duration.ofSeconds(120))
                                .build()
                )
                .build();
    }

    @Bean
    public S3Presigner r2S3Presigner() {

        properties.validate();

        return S3Presigner.builder()
                .endpointOverride(getEndpoint())
                .region(Region.of("auto"))
                .credentialsProvider(getCredentialsProvider())
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build()
                )
                .build();
    }

    /**
     * Uses R2_ENDPOINT when set, otherwise builds https://<account-id>.r2.cloudflarestorage.com.
     * Rejects anything that is not a plain https host (no path / bucket name, query or credentials).
     * Error messages never include the configured value.
     */
    private URI getEndpoint() {

        String endpoint = properties.getEndpoint();

        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://"
                    + properties.getAccountId().trim()
                    + ".r2.cloudflarestorage.com";
        }
        endpoint = endpoint.trim();

        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "R2 endpoint is not a valid URL. Check R2_ENDPOINT and R2_ACCOUNT_ID.");
        }

        String path = uri.getPath();
        boolean hasPath = path != null && !path.isEmpty() && !"/".equals(path);

        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || hasPath
                || uri.getQuery() != null
                || uri.getUserInfo() != null) {
            throw new IllegalStateException(
                    "R2_ENDPOINT must look like https://<account-id>.r2.cloudflarestorage.com "
                            + "(https, host only, no bucket name or path).");
        }

        return URI.create("https://" + uri.getHost()
                + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));
    }

    private StaticCredentialsProvider getCredentialsProvider() {

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKeyId(),
                properties.getSecretAccessKey()
        );

        return StaticCredentialsProvider.create(credentials);
    }
}