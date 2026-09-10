package com.travel_system.backend_app.service.strategies.profile;

import com.travel_system.backend_app.interfaces.UserProfileStrategy;
import com.travel_system.backend_app.model.PlatformAdministrator;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.PlatformAdministratorRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class PlatformAdministratorProfilePictureStrategy implements UserProfileStrategy {

    private final PlatformAdministratorRepository platformAdministratorRepository;

    public PlatformAdministratorProfilePictureStrategy(PlatformAdministratorRepository platformAdministratorRepository) {
        this.platformAdministratorRepository = platformAdministratorRepository;
    }

    @Override
    public boolean supports(UserAccountType userAccountType) {
        return userAccountType == UserAccountType.PLATFORM_ADMINISTRATOR;
    }

    @Override
    public void updatePicture(UserAccount userAccount, String pictureKey) {
        PlatformAdministrator platformAdministrator = platformAdministratorRepository.findByEmail(userAccount.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("PlatformAdministrator não encontrado pelo email:" + userAccount));

        platformAdministrator.setProfilePicture(pictureKey);
        platformAdministratorRepository.save(platformAdministrator);
    }

    @Override
    public void deletePicture(UserAccount userAccount) {
        PlatformAdministrator platformAdministrator = platformAdministratorRepository.findByEmail(userAccount.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("PlatformAdministrator não encontrado pelo email:" + userAccount));

        platformAdministrator.setProfilePicture(null);
        platformAdministratorRepository.save(platformAdministrator);
    }
}
