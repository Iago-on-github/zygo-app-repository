package com.travel_system.backend_app.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "storage.documents")
public class DocumentStorageProperties {
    @NotBlank
    private String bucket;
    @NotBlank
    private String presignEndpoint;
    @NotNull
    private Duration downloadUrlTtl;

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getPresignEndpoint() {
        return presignEndpoint;
    }

    public void setPresignEndpoint(String presignEndpoint) {
        this.presignEndpoint = presignEndpoint;
    }

    public Duration getDownloadUrlTtl() {
        return downloadUrlTtl;
    }

    public void setDownloadUrlTtl(Duration downloadUrlTtl) {
        this.downloadUrlTtl = downloadUrlTtl;
    }
}
