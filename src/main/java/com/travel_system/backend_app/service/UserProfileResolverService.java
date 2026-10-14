package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.MinorStudentNotAllowedException;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.HealthOwner;
import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.cglib.core.Local;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;
import java.util.UUID;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

/*
* operações com base na decisão do tipo de accountUser
* */

@Service
public class UserProfileResolverService {

    private final UserAccountRepository userAccountRepository;
    private final StudentRepository studentRepository;
    private final DriverRepository driverRepository;
    private final AdministratorRepository administratorRepository;
    private final ResponsibleAdultRepository responsibleAdultRepository;

    public UserProfileResolverService(UserAccountRepository userAccountRepository, StudentRepository studentRepository, DriverRepository driverRepository, AdministratorRepository administratorRepository, ResponsibleAdultRepository responsibleAdultRepository) {
        this.userAccountRepository = userAccountRepository;
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

    // aluno maior ou motorista agindo sobre os próprios dados
    public HealthOwner resolveSelf() {
        UserAccount account = userAccountRepository.findUserByEmail(getAuthenticatedUserEmail());

        if (account == null) {
            throw new AccessDeniedException("Conta não encontrada");
        }

        return switch (account.getUserAccountType()) {
            case STUDENT -> {
                Student student = studentRepository.findByUserAccountId(account.getId())
                        .orElseThrow(() -> new EntityNotFoundException("Student não encontrado"));

                if (isMinor(student.getBirthdate())) {
                    throw new MinorStudentNotAllowedException("Estudantes menores de idade não podem realizar essa ação, apenas seus responsáveis");
                }
                yield new HealthOwner(OwnerType.STUDENT, student.getId());
            }
            case DRIVER -> driverRepository.findByUserAccountId(account.getId())
                    .map(d -> new HealthOwner(OwnerType.DRIVER, d.getId()))
                    .orElseThrow(() -> new EntityNotFoundException("Driver não encontrado"));
            default -> throw new AccessDeniedException("Tipo de conta não permitido para essa ação");
        };
    }

    // responsável agindo por um aluno menor vinculado a ele
    public HealthOwner resolveForResponsible(UUID studentId) {
        UserAccount account = userAccountRepository.findUserByEmail(getAuthenticatedUserEmail());

        if (account == null) {
            throw new AccessDeniedException("Conta não encontrada");
        }

        if (account.getUserAccountType() != UserAccountType.RESPONSIBLE_ADULT) {
            throw new AccessDeniedException("Apenas responsáveis podem realizar essa ação");
        }

        Student student = studentRepository.findLinkedToResponsible(studentId, account.getId())
                .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado"));

        if (!isMinor(student.getBirthdate())) {
            throw new EntityNotFoundException("Estudante não encontrado");
        }

        return new HealthOwner(OwnerType.STUDENT, student.getId());
    }

    private boolean isMinor(LocalDate d1) {
        return Period.between(d1, LocalDate.now()).getYears() <= 17;
    }

}
