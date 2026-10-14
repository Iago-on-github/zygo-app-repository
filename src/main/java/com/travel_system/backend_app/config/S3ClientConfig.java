package com.travel_system.backend_app.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties({
        StorageProperties.class,
        DocumentStorageProperties.class
})
public class S3ClientConfig {

    private final StorageProperties storageProperties;
    private final DocumentStorageProperties documentProperties;

    public S3ClientConfig(StorageProperties storageProperties, DocumentStorageProperties documentProperties) {
        this.storageProperties = storageProperties;
        this.documentProperties = documentProperties;
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(storageProperties.getEndpoint()))
                .region(Region.of(storageProperties.getRegion()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(storageProperties.getAccessKey(), storageProperties.getSecretKey())
                        )
                )
                .forcePathStyle(true)
                .build();
    }

    /*
     *  gera URLs pré-assinadas para o bucket privado de documentos:
     * - usa o endpoint PÚBLICO (acessível pelo navegador), não o endpoint interno do Docker
     * - path-style é necessário para o MinIO
     * */
    @Bean
    public S3Presigner s3Presigner(StorageProperties storageProperties) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(documentProperties.getPresignEndpoint()))
                .region(Region.of(storageProperties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(storageProperties.getAccessKey(), storageProperties.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
