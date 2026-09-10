package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.exceptions.DomainValidationException;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.UserAccountRepository;
import com.travel_system.backend_app.interfaces.UserProfileStrategy;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class CurrentUserService {
    private final UserAccountRepository userAccountRepository;

    private final List<UserProfileStrategy> profileStrategies;
    private final S3StorageService s3StorageService;
    private final ImageProcessingService imageProcessingService;
    private final UserProfileResolverService userProfileResolverService;

    private final Logger logger = LoggerFactory.getLogger(CurrentUserService.class);

    public CurrentUserService(UserAccountRepository userAccountRepository, S3StorageService s3StorageService, ImageProcessingService imageProcessingService, UserProfileResolverService userProfileResolverService, TokenConfig tokenConfig, List<UserProfileStrategy> profileStrategies) {
        this.userAccountRepository = userAccountRepository;
        this.s3StorageService = s3StorageService;
        this.imageProcessingService = imageProcessingService;
        this.userProfileResolverService = userProfileResolverService;
        this.profileStrategies = profileStrategies;
    }

    // verifica se o adm logado é o adm da plataforma
    public boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        return auth.getAuthorities().stream().anyMatch(p -> p.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
    }

    // pega o email do user autenticado
    public static String getAuthenticatedUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            throw new AccessDeniedException("Usuário não autenticado");
        }
        return authentication.getName();
    }

    // adiciona/atualiza profilePicture
    @Transactional
    public void updateMyProfilePicture(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }

        String email = getAuthenticatedUserEmail();
        UserAccount userAccount = userAccountRepository.findUserByEmail(email);

        if (userAccount == null) {
            throw new EntityNotFoundException("UserAccount não encontrado");
        }

        String pictureKey = generateProfilePictureKey(userAccount);
        byte[] bytes = imageProcessingService.convertImageToJPEG(file);
        s3StorageService.upload(bytes, pictureKey, "image/jpeg");

        // handler
        findHandler(userAccount.getUserAccountType()).updatePicture(userAccount, pictureKey);
    }

    // remove profilePicture
    @Transactional
    public void deleteMyProfilePicture() {
        String email = getAuthenticatedUserEmail();
        UserAccount userAccount = userAccountRepository.findUserByEmail(email);

        if (userAccount == null) {
            throw new EntityNotFoundException("UserAccount não encontrado");
        }

        findHandler(userAccount.getUserAccountType()).deletePicture(userAccount);
    }

    // retorna a url publica que o front usa para consumir as fotos
    public String getPublicUrl(String objectKey) {
        return s3StorageService.getPublicUrl(objectKey);
    }

    // decide se é customer ou platform e monta a key
    private String generateProfilePictureKey(UserAccount userAccount) {
        UUID customerId = userProfileResolverService.resolveCustomerId(userAccount);
        UUID userId = userAccount.getId();

        if (customerId == null) {
            return "platform/users/" + userId + "/profile.jpeg";
        }
        return "customers/" + customerId + "/users/" + userId + "/profile.jpeg";
    }

    private UserProfileStrategy findHandler(UserAccountType type) {
        return profileStrategies.stream()
                .filter(h -> h.supports(type))
                .findFirst()
                .orElseThrow(() -> new DomainValidationException("Nenhum handler registrado para o tipo: " + type));
    }
}
