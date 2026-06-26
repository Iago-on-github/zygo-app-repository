package com.travel_system.backend_app.interfaces;

import org.springframework.web.multipart.MultipartFile;

public interface StorageInterfaceService {
    String upload(byte[] bytes, String objectKey, String contentType);

    void delete(String objectKey);

    String getPublicUrl(String objectKey);

//    String getPresignedUrl(String objectKey); envio de docs privados
}
