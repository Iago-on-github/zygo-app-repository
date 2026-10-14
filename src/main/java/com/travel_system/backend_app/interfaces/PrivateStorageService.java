package com.travel_system.backend_app.interfaces;

public interface PrivateStorageService {

    void upload(byte[] content, String key, String contentType);

    void delete(String key);

    String generateDownloadUrl(String key, String downloadFileName);
}
