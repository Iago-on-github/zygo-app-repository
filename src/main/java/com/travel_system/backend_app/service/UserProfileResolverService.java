package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.AdministratorRepository;
import com.travel_system.backend_app.repository.DriverRepository;
import com.travel_system.backend_app.repository.ResponsibleAdultRepository;
import com.travel_system.backend_app.repository.StudentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/*
* operações com base na decisão do tipo de accountUser
* */

@Service
public class UserProfileResolverService {

    private final StudentRepository studentRepository;
    private final DriverRepository driverRepository;
    private final AdministratorRepository administratorRepository;
    private final ResponsibleAdultRepository responsibleAdultRepository;

    public UserProfileResolverService(StudentRepository studentRepository, DriverRepository driverRepository, AdministratorRepository administratorRepository, ResponsibleAdultRepository responsibleAdultRepository) {
        this.studentRepository = studentRepository;
        this.driverRepository = driverRepository;
        this.administratorRepository = administratorRepository;
        this.responsibleAdultRepository = responsibleAdultRepository;
    }

    public UUID resolveCustomerId(UserAccount userAccount) {
        if (userAccount == null) {
            throw new IllegalArgumentException("UserAccount não pode ser null");
        }

        if (userAccount.getUserAccountType() == null) {
            throw new IllegalStateException("UserAccount " + userAccount.getId() + " não possui UserAccountType");
        }

        return switch (userAccount.getUserAccountType()) {
            case STUDENT -> studentRepository.findByUserAccountId(userAccount.getId())
                    .map(Student::getCustomerId)
                    .orElseThrow(() -> new IllegalStateException("UserAccount do tipo STUDENT não possui perfil Student associado"));

            case DRIVER -> driverRepository.findByUserAccountId(userAccount.getId())
                    .map(Driver::getCustomerId)
                    .orElseThrow(() -> new IllegalStateException("UserAccount do tipo DRIVER não possui perfil Driver associado"));

            case ADMINISTRATOR -> administratorRepository.findByUserAccountId(userAccount.getId())
                    .map(Administrator::getCustomerId)
                    .orElseThrow(() -> new IllegalStateException("UserAccount do tipo ADMINISTRATOR não possui perfil Administrator associado"));

            case RESPONSIBLE_ADULT -> responsibleAdultRepository.findByUserAccountId(userAccount.getId())
                    .map(ResponsibleAdult::getCustomerId)
                    .orElseThrow(() -> new IllegalStateException("UserAccount do tipo RESPONSIBLE_ADULT não possui perfil ResponsibleAdult associado"));

            case PLATFORM_ADMINISTRATOR -> null;

            // caso o usuário global seja "unassigned", retorna null
            case UNASSIGNED -> null;
        };
    }

}
