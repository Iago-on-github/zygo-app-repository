package com.travel_system.backend_app.service;

import com.travel_system.backend_app.interfaces.PrivateStorageService;
import com.travel_system.backend_app.interfaces.ProfilePictureStrategy;
import com.travel_system.backend_app.interfaces.StorageInterfaceService;
import com.travel_system.backend_app.model.enums.ImageUploadPolicy;
import com.travel_system.backend_app.repository.UserAccountRepository;
import com.travel_system.backend_app.resolvers.UserProfileResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class ImageStorageService {

    private final UserAccountRepository userAccountRepository;

    private final UserProfileResolver userProfileResolver;
    private final ImageProcessingService imageProcessingService;
    private final PrivateStorageService privateStorageService;
    private final StorageInterfaceService storageService;
    private final List<ProfilePictureStrategy> profileStrategies;

    // formato gravado no storage via conversão sempre gera JPEG
    private static final String OUTPUT_CONTENT_TYPE = "image/jpeg";

    public ImageStorageService(UserAccountRepository userAccountRepository, UserProfileResolver userProfileResolver, ImageProcessingService imageProcessingService, S3StorageService s3StorageService, PrivateStorageService privateStorageService, StorageInterfaceService storageService, List<ProfilePictureStrategy> profileStrategies) {
        this.userAccountRepository = userAccountRepository;
        this.userProfileResolver = userProfileResolver;
        this.imageProcessingService = imageProcessingService;
        this.privateStorageService = privateStorageService;
        this.storageService = storageService;
        this.profileStrategies = profileStrategies;
    }

    public String storeProfilePicture(MultipartFile file, String key) throws IOException {
        byte[] bytes = imageProcessingService.convertImageToJPEG(file, ImageUploadPolicy.PROFILE_PICTURE);
        storageService.upload(bytes, key, OUTPUT_CONTENT_TYPE);

        return key;
    }

    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        storageService.delete(key);
    }


}
