package com.travel_system.backend_app.service;

import com.travel_system.backend_app.interfaces.StudentEmergencyContactView;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.OwnerBuilt;
import com.travel_system.backend_app.model.dtos.response.EmergencyContactDTO;
import com.travel_system.backend_app.model.dtos.response.HealthConditionSummaryDTO;
import com.travel_system.backend_app.model.dtos.response.StudentHealthSummaryDTO;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

/*
* responsável por dados sensíveis (como documentos) para o motorista durante a viagem
* */

@Service
public class TravelHealthSummaryService {

    private final UserAccountRepository userAccountRepository;
    private final DriverRepository driverRepository;
    private final StudentRepository studentRepository;
    private final TravelRepository travelRepository;
    private final HealthDeclarationRepository healthDeclarationRepository;
    private final ResponsibleAdultRepository responsibleAdultRepository;

    private final UserProfileResolverService userProfileResolverService;
    private final SensitiveDataAccessLogService sensitiveDataAccessLogService;

    public TravelHealthSummaryService(UserAccountRepository userAccountRepository, DriverRepository driverRepository, StudentRepository studentRepository, TravelRepository travelRepository, HealthDeclarationRepository healthDeclarationRepository, ResponsibleAdultRepository responsibleAdultRepository, UserProfileResolverService userProfileResolverService, SensitiveDataAccessLogService sensitiveDataAccessLogService) {
        this.userAccountRepository = userAccountRepository;
        this.driverRepository = driverRepository;
        this.studentRepository = studentRepository;
        this.travelRepository = travelRepository;
        this.healthDeclarationRepository = healthDeclarationRepository;
        this.responsibleAdultRepository = responsibleAdultRepository;
        this.userProfileResolverService = userProfileResolverService;
        this.sensitiveDataAccessLogService = sensitiveDataAccessLogService;
    }

    /*
     * Resumo de saúde dos alunos da viagem em andamento do motorista autenticado
     * Regras básicas
     *  - somente viagem TRAVELLING (acesso só após o início)
     *  - somente alunos com condições declaradas
     *  - aluno menor: contatos de emergência vêm dos responsáveis
     *  - cada declaração exibida gera um registro de acesso a dado sensível
     */
    @Transactional(readOnly = true)
    public List<StudentHealthSummaryDTO> getCurrentSummary() {
        UUID actorAccountId = getAuthenticatedUserAccountId();

        Driver driver = driverRepository.findByUserAccountId(actorAccountId)
                .orElseThrow(() -> new EntityNotFoundException("Motorista não encontrado"));

        Travel travel = travelRepository.findFirstByDriverIdAndTravelStatus(driver.getId(), TravelStatus.TRAVELLING)
                .orElseThrow(() -> new EntityNotFoundException("Nenhuma viagem em andamento"));

        List<UUID> studentIds = travel.getStudentTravels().stream().map(StudentTravel::getStudent).map(Student::getId).toList();
        if (studentIds.isEmpty()) {
            return List.of();
        }

        // uma consulta para todas as declarações com condições
        List<HealthDeclaration> declarations = healthDeclarationRepository.findWithConditionsByStudentIdIn(studentIds);
        if (declarations.isEmpty()) {
            return List.of();
        }

        // alunos que aparecem no resumo (nome + menoridade)
        Map<UUID, Student> students = studentRepository.findAllById(declarations.stream().map(HealthDeclaration::getStudentId).toList())
                .stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));

        // contatos dos responsáveis, só para os menores, em lote
        List<UUID> minorIds = students.values().stream()
                .filter(s -> userProfileResolverService.isMinor(s.getBirthdate()))
                .map(Student::getId)
                .toList();

        Map<UUID, List<EmergencyContactDTO>> responsibleContacts = minorIds.isEmpty() ? Map.of() : responsibleAdultRepository.findEmergencyContactsByStudentIdIn(minorIds).stream()
                .collect(Collectors.groupingBy(
                        StudentEmergencyContactView::getStudentId,
                        Collectors.mapping(c -> new EmergencyContactDTO(c.getName(), c.getTelephone()),
                                Collectors.toList())
                ));

        List<StudentHealthSummaryDTO> summaries = new ArrayList<>();

        for (HealthDeclaration declaration : declarations) {
            Student student = students.get(declaration.getStudentId());
            if (student == null) {
                continue;
            }

            List<HealthConditionSummaryDTO> conditions = declaration.getConditions().stream()
                    .map(c -> new HealthConditionSummaryDTO(
                            c.getType(),
                            c.getSeverity(),
                            c.getDescription(),
                            c.getEmergencyInstructions(),
                            c.getMedication(),
                            c.getWhereIsMedication()
                    ))
                    .toList();

            // menor -> responsáveis; maior -> contatos informados nas próprias condições
            List<EmergencyContactDTO> contacts = userProfileResolverService.isMinor(student.getBirthdate())
                    ? responsibleContacts.getOrDefault(student.getId(), List.of())
                    : declaration.getConditions().stream()
                    .filter(c -> c.getEmergencyContactTelephone() != null)
                    .map(c -> new EmergencyContactDTO(c.getEmergencyContactName(), c.getEmergencyContactTelephone()))
                    .distinct()
                    .toList();

            summaries.add(new StudentHealthSummaryDTO(
                    student.getId(),
                    student.getName(),
                    declaration.getHealthDeclarationStatus() == HealthDeclarationStatus.OUTDATED,
                    conditions,
                    contacts
            ));

            // ator = conta do motorista; recurso = declaração exibida (REQUIRES_NEW, sobrevive ao readOnly)
            sensitiveDataAccessLogService.record(actorAccountId, new OwnerBuilt(OwnerType.STUDENT, student.getId()), ResourceType.HEALTH_DECLARATION, declaration.getId(), Action.VIEW, travel.getId());
        }

        return summaries;
    }


    private UUID getAuthenticatedUserAccountId() {
        String email = getAuthenticatedUserEmail();

        UserAccount user = userAccountRepository.findUserByEmail(email);

        if (user == null) {
            throw new AccessDeniedException("Acesso negado, user não existe.");
        }

        return user.getId();
    }
}
