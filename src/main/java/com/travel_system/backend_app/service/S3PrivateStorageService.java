package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.DocumentStorageProperties;
import com.travel_system.backend_app.interfaces.PrivateStorageService;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class S3PrivateStorageService implements PrivateStorageService {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final DocumentStorageProperties properties;

    public S3PrivateStorageService(S3Client s3Client, S3Presigner s3Presigner, DocumentStorageProperties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    // grava o documento no bucket privado sem nenhuma política de leitura pública
    @Override
    public void upload(byte[] content, String key, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(contentType)
                .contentLength((long) content.length)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(content));
    }

    // remove o documento de forma idempotente (apagar uma chave inexistente não gera erro)
    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build());
    }


    @Override
    public String generatePresignPhotoUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        return properties.getPresignEndpoint() + "/" + key;
    }

    /*
     *  gera uma URL temporária para download, steps:
     * - expira após o TTL configurado
     * - força o download (attachment) e o tipo PDF na resposta, ambos assinados na URL
     * - só deve ser chamado DEPOIS da checagem de permissão no service de documentos
     * */
    @Override
    public String generateDownloadUrl(String key, String downloadFileName) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .responseContentType("application/pdf")
                .responseContentDisposition("attachment; filename=\"" + sanitizeFilename(downloadFileName) + "\"")
                .build();

        GetObjectPresignRequest presignRequest  = GetObjectPresignRequest.builder()
                .signatureDuration(properties.getDownloadUrlTtl())
                .getObjectRequest(getRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    // mantém apenas caracteres seguros no nome exibido no download
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "documento.pdf";
        }

        String sanitized = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.toLowerCase().endsWith(".pdf") ? sanitized : sanitized + ".pdf";
    }
}
