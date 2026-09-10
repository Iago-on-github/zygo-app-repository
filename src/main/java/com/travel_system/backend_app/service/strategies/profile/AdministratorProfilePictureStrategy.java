package com.travel_system.backend_app.service.strategies.profile;

import com.travel_system.backend_app.interfaces.UserProfileStrategy;
import com.travel_system.backend_app.model.Administrator;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.AdministratorRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class AdministratorProfilePictureStrategy implements UserProfileStrategy {

    private final AdministratorRepository administratorRepository;

    public AdministratorProfilePictureStrategy(AdministratorRepository administratorRepository) {
        this.administratorRepository = administratorRepository;
    }

    @Override
    public boolean supports(UserAccountType userAccountType) {
        return userAccountType == UserAccountType.ADMINISTRATOR;
    }

    @Override
    public void updatePicture(UserAccount userAccount, String pictureKey) {
        Administrator administrator = administratorRepository.findByEmail(userAccount.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("Administrator não encontrado pelo Email: " + userAccount));

        administrator.setProfilePicture(pictureKey);
        administratorRepository.save(administrator);
    }

    @Override
    public void deletePicture(UserAccount userAccount) {
        Administrator administrator = administratorRepository.findByEmail(userAccount.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("Administrator não encontrado pelo Email: " + userAccount));

        administrator.setProfilePicture(null);
        administratorRepository.save(administrator);
    }
}
