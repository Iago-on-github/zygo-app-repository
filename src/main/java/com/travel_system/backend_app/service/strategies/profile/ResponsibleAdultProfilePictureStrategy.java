package com.travel_system.backend_app.service.strategies.profile;

import com.travel_system.backend_app.interfaces.UserProfileStrategy;
import com.travel_system.backend_app.model.ResponsibleAdult;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.ResponsibleAdultRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class ResponsibleAdultProfilePictureStrategy implements UserProfileStrategy {

    private final ResponsibleAdultRepository responsibleAdultRepository;

    public ResponsibleAdultProfilePictureStrategy(ResponsibleAdultRepository responsibleAdultRepository) {
        this.responsibleAdultRepository = responsibleAdultRepository;
    }

    @Override
    public boolean supports(UserAccountType userAccountType) {
        return userAccountType == UserAccountType.RESPONSIBLE_ADULT;
    }

    @Override
    public void updatePicture(UserAccount userAccount, String pictureKey) {
        ResponsibleAdult responsibleAdult = responsibleAdultRepository.findByEmail(userAccount.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("Entidade ResponsibleAdult não encontrada pelo email: " + userAccount.getEmail()));

        responsibleAdult.setProfilePicture(pictureKey);

        responsibleAdultRepository.save(responsibleAdult);
    }

    @Override
    public void deletePicture(UserAccount userAccount) {
        ResponsibleAdult responsibleAdult = responsibleAdultRepository.findByEmail(userAccount.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("Entidade ResponsibleAdult não encontrada pelo email: " + userAccount.getEmail()));

        responsibleAdult.setProfilePicture(null);

        responsibleAdultRepository.save(responsibleAdult);
    }
}
