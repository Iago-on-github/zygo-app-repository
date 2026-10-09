package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.DomainValidationException;
import com.travel_system.backend_app.exceptions.FileTooLargeException;
import com.travel_system.backend_app.interfaces.ProfilePictureStrategy;
import com.travel_system.backend_app.interfaces.StorageInterfaceService;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class ImageStorageService {

    private final UserAccountRepository userAccountRepository;

    private final UserProfileResolverService userProfileResolverService;
    private final ImageProcessingService imageProcessingService;
    private final StorageInterfaceService storageService;
    private final List<ProfilePictureStrategy> profileStrategies;

    // formato gravado no storage via conversão sempre gera JPEG
    private static final String OUTPUT_CONTENT_TYPE = "image/jpeg";

    // formatos aceitos no envio
    private static final Set<String> ACCEPTED_INPUT_CONTENT_TYPES = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");
    private static final long MAX_UPLOAD_SIZE_BYTES = 150 * 1024;

    public ImageStorageService(UserAccountRepository userAccountRepository, UserProfileResolverService userProfileResolverService, ImageProcessingService imageProcessingService, S3StorageService s3StorageService, StorageInterfaceService storageService, List<ProfilePictureStrategy> profileStrategies) {
        this.userAccountRepository = userAccountRepository;
        this.userProfileResolverService = userProfileResolverService;
        this.imageProcessingService = imageProcessingService;
        this.storageService = storageService;
        this.profileStrategies = profileStrategies;
    }

    public String store(MultipartFile file, String key) throws IOException {
        validate(file);

        byte[] bytes = imageProcessingService.convertImageToJPEG(file);
        storageService.upload(bytes, key, OUTPUT_CONTENT_TYPE);

        return key;
    }


    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        storageService.delete(key);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            throw new FileTooLargeException("O arquivo é muito grande. Uploads aceitos são de até 150 KB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ACCEPTED_INPUT_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Formato de imagem não suportado. Envie JPEG, PNG ou WebP");
        }
    }

}
