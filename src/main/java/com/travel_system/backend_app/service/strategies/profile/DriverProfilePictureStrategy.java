package com.travel_system.backend_app.service.strategies.profile;

import com.travel_system.backend_app.interfaces.UserProfileStrategy;
import com.travel_system.backend_app.model.Driver;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.DriverRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class DriverProfilePictureStrategy implements UserProfileStrategy {

    private final DriverRepository driverRepository;

    public DriverProfilePictureStrategy(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @Override
    public boolean supports(UserAccountType userAccountType) {
        return userAccountType == UserAccountType.DRIVER;
    }

    @Override
    public void updatePicture(UserAccount userAccount, String pictureKey) {
        Driver driver = driverRepository.findByEmail(userAccount.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("Driver não encontrado pelo email: " + userAccount.getEmail()));

        driver.setProfilePicture(pictureKey);
        driverRepository.save(driver);
    }

    @Override
    public void deletePicture(UserAccount userAccount) {
        Driver driver = driverRepository.findByEmail(userAccount.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("Driver não encontrado pelo email: " + userAccount.getEmail()));

        driver.setProfilePicture(null);
        driverRepository.save(driver);
    }
}
