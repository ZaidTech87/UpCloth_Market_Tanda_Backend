
package com.clothmarket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "r2")
public class R2Properties {

    private String accountId;
    private String accessKeyId;
    private String secretAccessKey;
    private String bucketName;
    private String endpoint;

    private long signedUrlTtlVoiceSeconds = 3600;
    private long signedUrlTtlMediaSeconds = 21600;

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public long getSignedUrlTtlVoiceSeconds() {
        return signedUrlTtlVoiceSeconds;
    }

    public void setSignedUrlTtlVoiceSeconds(long value) {
        this.signedUrlTtlVoiceSeconds = value;
    }

    public long getSignedUrlTtlMediaSeconds() {
        return signedUrlTtlMediaSeconds;
    }

    public void setSignedUrlTtlMediaSeconds(long value) {
        this.signedUrlTtlMediaSeconds = value;
    }

    public void validate() {

        if (isBlank(accountId)
                || isBlank(accessKeyId)
                || isBlank(secretAccessKey)
                || isBlank(bucketName)) {

            throw new IllegalStateException(
                    "Cloudflare R2 configuration is incomplete. "
                            + "Check R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, "
                            + "R2_SECRET_ACCESS_KEY and R2_BUCKET_NAME."
            );
        }

        if (signedUrlTtlVoiceSeconds <= 0
                || signedUrlTtlMediaSeconds <= 0
                || signedUrlTtlVoiceSeconds > 604800
                || signedUrlTtlMediaSeconds > 604800) {

            throw new IllegalStateException(
                    "R2 signed URL TTL must be between 1 and 604800 seconds."
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
