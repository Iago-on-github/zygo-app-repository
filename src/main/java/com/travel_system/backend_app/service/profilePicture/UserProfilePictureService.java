package com.travel_system.backend_app.service.profilePicture;

import com.travel_system.backend_app.exceptions.DomainValidationException;
import com.travel_system.backend_app.interfaces.ProfilePictureStrategy;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.UserAccountRepository;
import com.travel_system.backend_app.service.ImageStorageService;
import com.travel_system.backend_app.service.UserProfileResolverService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class UserProfilePictureService {

    private final UserAccountRepository userAccountRepository;

    private final List<ProfilePictureStrategy> profileStrategies;
    private final UserProfileResolverService userProfileResolverService;
    private final ImageStorageService imageStorageService;

    public UserProfilePictureService(UserAccountRepository userAccountRepository, List<ProfilePictureStrategy> profileStrategies, UserProfileResolverService userProfileResolverService, ImageStorageService imageStorageService) {
        this.userAccountRepository = userAccountRepository;
        this.profileStrategies = profileStrategies;
        this.userProfileResolverService = userProfileResolverService;
        this.imageStorageService = imageStorageService;
    }

    @Transactional
    public void updateMyProfilePicture(MultipartFile file) throws IOException {
        UserAccount userAccount = getAuthenticatedUserAccount();

        String pictureKey = imageStorageService.store(file, generateProfilePictureKey(userAccount));

        findHandler(userAccount.getUserAccountType()).updatePicture(userAccount, pictureKey);
    }

    @Transactional
    public void deleteMyProfilePicture() {
        UserAccount userAccount = getAuthenticatedUserAccount();

        findHandler(userAccount.getUserAccountType()).deletePicture(userAccount);
    }

    private UserAccount getAuthenticatedUserAccount() {
        UserAccount userAccount = userAccountRepository.findUserByEmail(getAuthenticatedUserEmail());
        if (userAccount == null) {
            throw new EntityNotFoundException("UserAccount não encontrado");
        }
        return userAccount;
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

    private ProfilePictureStrategy findHandler(UserAccountType type) {
        return profileStrategies.stream()
                .filter(h -> h.supports(type))
                .findFirst()
                .orElseThrow(() -> new DomainValidationException("Nenhum handler registrado para o tipo: " + type));
    }

}
