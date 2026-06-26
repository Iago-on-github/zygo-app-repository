package com.travel_system.backend_app.service;

import com.google.api.client.util.Value;
import com.travel_system.backend_app.exceptions.StorageException;
import com.travel_system.backend_app.interfaces.StorageInterfaceService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.awt.*;
import java.io.IOException;

/*
* implementação concreta usando AWS SDK
* mesmo nomeado como s3 da amazon, ele servira tanto para o minIO em DEV quanto para o R2 (cloudflare) em PROD
* ambos são compatíveis com o S3 da AWS
*/

@Service
public class S3StorageService implements StorageInterfaceService {
    @Value("${storage.bucket-name}")
    private String bucketName;
    @Value("${storage.public-base-url}")
    private String publicBaseUrl;

    private final S3Client s3Client;

    public S3StorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    // recebe os bytes prontos (já convertidos) e realiza o upload
    @Override
    public String upload(byte[] bytes, String objectKey, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("[upload] - bytes vazios ou null");
        }

        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("[upload] - objectKey inválida");
        }

        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("[upload] - contentType inválido");
        }

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(contentType) // brower needs to know image type
                    .contentLength((long) bytes.length) // stream length
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(bytes));

            return objectKey; // retorna a objectKey ao invés da URL completa
        } catch (S3Exception e) {
            throw new StorageException("[upload] erro ao enviar arquivo para storage", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("[delete] - objectKey inválida");
        }

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
        } catch (S3Exception e) {
            throw new StorageException("[delete] erro ao remover arquivo do storage", e);
        }
    }

    @Override
    public String getPublicUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("[getPublicUrl] - objectKey inválida");
        }

        return publicBaseUrl + "/" + objectKey;
    }
}
