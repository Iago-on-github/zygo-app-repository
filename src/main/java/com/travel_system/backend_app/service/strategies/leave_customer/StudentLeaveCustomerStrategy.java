package com.travel_system.backend_app.service.strategies.leave_customer;

import com.travel_system.backend_app.exceptions.ResourceInUseException;
import com.travel_system.backend_app.interfaces.LeaveCustomerStrategy;
import com.travel_system.backend_app.model.Student;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.StudentRepository;
import com.travel_system.backend_app.repository.StudentRouteStopAssignmentRepository;
import com.travel_system.backend_app.repository.TravelRepository;
import com.travel_system.backend_app.service.TravelService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Component
public class StudentLeaveCustomerStrategy implements LeaveCustomerStrategy {

    private final StudentRepository studentRepository;
    private final TravelRepository travelRepository;
    private final StudentRouteStopAssignmentRepository routeStopAssignmentRepository;

    private static final List<TravelStatus> BLOCKING_STATUSES = List.of(TravelStatus.SCHEDULED, TravelStatus.PENDING, TravelStatus.TRAVELLING);

    public StudentLeaveCustomerStrategy(StudentRepository studentRepository, TravelRepository travelRepository, StudentRouteStopAssignmentRepository routeStopAssignmentRepository) {
        this.studentRepository = studentRepository;
        this.travelRepository = travelRepository;
        this.routeStopAssignmentRepository = routeStopAssignmentRepository;
    }

    @Override
    public boolean supports(UserAccountType type) {
        return type == UserAccountType.STUDENT;
    }

    @Override
    public void validateLeave(UserAccount account) {
        Student student = findStudent();

        // se o estudante estiver em uma viagem não deixa desativar
        if (travelRepository.existsByStudentIdAndStatusIn(student.getId(), BLOCKING_STATUSES)) {
            throw new ResourceInUseException("Não é possível sair do espaço: você possui viagem agendada, pendente ou em andamento.");
        }
    }

    @Override
    public void unlinkFromCustomer(UserAccount account) {
        Student student = findStudent();

        student.setUserAccount(null);
        student.setStatus(GeneralStatus.INACTIVE);
        student.setLeftAt(Instant.now());

        // remove os dados de rotas
        routeStopAssignmentRepository.deleteAllByStudentId(student.getId());
        student.getStudentShift().clear();
        student.setResponsibleAdult(null);

        // matrículas
        student.getEnrollments().clear();

        // demais dados pessoais (preseva nome para histórico)
        student.setTelephone(null);
        student.setCpf(null);
        student.setBirthdate(null);
        student.setAddress(null);
    }

    private Student findStudent() {
        return studentRepository.findByEmail(getAuthenticatedUserEmail()).orElseThrow(() -> new AccessDeniedException("Student não encontrado"));
    }
}
