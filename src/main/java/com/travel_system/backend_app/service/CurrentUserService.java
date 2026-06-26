package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.UserModel;
import com.travel_system.backend_app.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class CurrentUserService {
    private final UserRepository userRepository;

    private final S3StorageService s3StorageService;
    private final ImageProcessingService imageProcessingService;

    private Logger logger = LoggerFactory.getLogger(CurrentUserService.class);

    public CurrentUserService(UserRepository userRepository, S3StorageService s3StorageService, ImageProcessingService imageProcessingService) {
        this.userRepository = userRepository;
        this.s3StorageService = s3StorageService;
        this.imageProcessingService = imageProcessingService;
    }

    // verifica se o adm logado é o adm da plataforma
    public boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        return auth.getAuthorities().stream().anyMatch(p -> p.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
    }

    public void userProfilePictureUpdate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("[userProfilePictureUpdate] - file não pode estar null");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = auth.getName();

        String profilePictureKey = generateProfilePictureKey(userEmail);

        byte[] bytes = imageProcessingService.convertImageToWebp(file);
        s3StorageService.upload(bytes, profilePictureKey, "image/webp");

        int updatedPicture = userRepository.updateProfilePicture(profilePictureKey, userEmail);
        logger.info("countSavedWithSuccess {}", updatedPicture);
    }

    // decide se é customer ou platform e monta a key
    private String generateProfilePictureKey(String userEmail) {
        boolean isPlatformAdmin = isPlatformAdmin();

        UserModel savedUser = userRepository.findUserByEmail(userEmail);

        if (savedUser == null) throw new EntityNotFoundException("Entidade com email " + userEmail + " não encontrada.");

        UUID customerId = null;
        if (!isPlatformAdmin) {
            customerId = savedUser.getCustomer().getId();
        }

        UUID userId = savedUser.getId();

        String platformAdminKey = "platform/users/" + userId + "/profile.webp";
        String customerKey = "customers/" + customerId + "/users/" + userId + "/profile.webp";

        return isPlatformAdmin ? platformAdminKey : customerKey;
    }
}
