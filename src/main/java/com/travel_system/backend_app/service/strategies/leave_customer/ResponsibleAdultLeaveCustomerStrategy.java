package com.travel_system.backend_app.service.strategies.leave_customer;

import com.travel_system.backend_app.exceptions.MinorStudentResponsibleAdultTransferRequiredException;
import com.travel_system.backend_app.interfaces.LeaveCustomerStrategy;
import com.travel_system.backend_app.model.ResponsibleAdult;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.ResponsibleAdultRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Component
public class ResponsibleAdultLeaveCustomerStrategy implements LeaveCustomerStrategy {

    private final ResponsibleAdultRepository responsibleAdultRepository;

    public ResponsibleAdultLeaveCustomerStrategy(ResponsibleAdultRepository responsibleAdultRepository) {
        this.responsibleAdultRepository = responsibleAdultRepository;
    }

    @Override
    public boolean supports(UserAccountType type) {
        return type == UserAccountType.RESPONSIBLE_ADULT;
    }

    @Override
    public void validateLeave(UserAccount account) {
        ResponsibleAdult responsible = findResponsible();

        LocalDate today = LocalDate.now();

        // se tiver alunos menores de idade lança exception
        boolean hasMinorStudent = responsible.getStudents().stream()
                .map(Student::getBirthdate)
                .anyMatch(birthdate -> birthdate == null || Period.between(birthdate, today).getYears() < 18);

        if (hasMinorStudent) {
            throw new MinorStudentResponsibleAdultTransferRequiredException("Existem alunos menores de idade vinculados a você. Peça ao administrador para indicar outro responsável antes de sair.");
        }
    }

    @Override
    public void unlinkFromCustomer(UserAccount account) {
        ResponsibleAdult responsible = findResponsible();

        // remove vínculo do aluno
        for (Student student : responsible.getStudents()) {
            student.setResponsibleAdult(null);
            student.setStudentRelationshipType(null);
        }
        responsible.getStudents().clear();

        responsible.setUserAccount(null);
        responsible.setStatus(GeneralStatus.INACTIVE);
        responsible.setLeftAt(Instant.now());

        // dados pessoais (alguns ficam para histórico)
        responsible.setCpf(null);
        responsible.setTelephone(null);
        responsible.setBirthdate(null);
        responsible.setAddress(null);
    }

    private ResponsibleAdult findResponsible() {
        return responsibleAdultRepository.findByEmail(getAuthenticatedUserEmail()).orElseThrow(() -> new AccessDeniedException("Responsible não encontrado"));
    }
}
