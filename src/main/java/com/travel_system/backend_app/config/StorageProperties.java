package com.travel_system.backend_app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String region;
    private String bucketPicture;
    private String publicBaseUrl;
    private String bucketDocuments;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucketPicture() {
        return bucketPicture;
    }

    public void setBucketPicture(String bucketPicture) {
        this.bucketPicture = bucketPicture;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getBucketDocuments() {
        return bucketDocuments;
    }

    public void setBucketDocuments(String bucketDocuments) {
        this.bucketDocuments = bucketDocuments;
    }
}
