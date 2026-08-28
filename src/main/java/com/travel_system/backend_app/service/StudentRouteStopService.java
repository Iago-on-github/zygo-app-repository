package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.RouteStopResponseMapper;
import com.travel_system.backend_app.interfaces.mappers.StudentRouteStopResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.RouteStopStudentUpdateDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopStudentsRequestDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentRouteStopAssociateResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StudentRouteStopService {
    private final UserRepository userRepository;
    private final RouteStopRepository routeStopRepository;
    private final StudentRepository studentRepository;
    private final StandardRouteRepository standardRouteRepository;
    private final StudentRouteStopAssignmentRepository studentRouteStopAssignmentRepository;

    private final StudentRouteStopResponseMapper studentRouteStopResponseMapper;

    public StudentRouteStopService(UserRepository userRepository, RouteStopRepository routeStopRepository, StudentRepository studentRepository, StandardRouteRepository standardRouteRepository, StudentRouteStopAssignmentRepository studentRouteStopAssignmentRepository, StudentRouteStopResponseMapper studentRouteStopResponseMapper) {
        this.userRepository = userRepository;
        this.routeStopRepository = routeStopRepository;
        this.studentRepository = studentRepository;
        this.standardRouteRepository = standardRouteRepository;
        this.studentRouteStopAssignmentRepository = studentRouteStopAssignmentRepository;
        this.studentRouteStopResponseMapper = studentRouteStopResponseMapper;
    }

    /*
    * retorna os routeStops do estudante com base no customer e na rota padrão específica
    * */
    @Transactional(readOnly = true)
    public List<StudentRouteStopAssociateResponseDTO> getStudentRouteStops(String authenticatedEmail, UUID studentId, UUID standardRouteId) {
        UserModel authenticatedUser = userRepository.findUserByEmail(authenticatedEmail);

        if (authenticatedUser == null) throw new EntityNotFoundException("Usuário com o email " + authenticatedEmail + " não encontrado");

        StandardRoute standardRoute = standardRouteRepository.findById(standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException("Rota Padrão não encontrada: " + standardRouteId));

        // verifica se o user é válido (estudante, admin, platform_admin)
        checkValidUser(authenticatedUser);

        // valida mesmo Customer
        validateSameCustomer(authenticatedUser.getCustomerId(), standardRoute.getCustomerId());

        boolean isStudent = authenticatedUser.getRoles().stream().anyMatch(role -> role.equals("ROLE_USER"));

        UUID customerId = authenticatedUser.getCustomerId();

        UUID targetStudentId;
        if (isStudent) {
            // estudantes só podem consultar seus próprios dados, ignorando o ID passado na requisição
            targetStudentId = authenticatedUser.getId();
        } else {
            // admins podem consultar outros estudantes, mas precisamos validar o customer do estudante alvo
            targetStudentId = studentId;
            Student targetStudent = studentRepository.findById(targetStudentId)
                    .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado: " + targetStudentId));

            validateSameCustomer(customerId, targetStudent.getCustomerId());
        }

        StudentRouteStopAssignment assignment = studentRouteStopAssignmentRepository.findByStudentIdAndStandardRouteId(targetStudentId, standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException("Entidade de relacionamento não encontrada"));

        Set<UUID> studentIds = resolveStudentIds(assignment.getRouteStop().getId(), standardRoute.getId());

        return List.of(studentRouteStopResponseMapper.toDTO(assignment, studentIds));
    }

    @Transactional(readOnly = true)
    public StudentRouteStopAssociateResponseDTO getStudentRouteStopsByPeriodAndStandardRoute(String authenticatedEmail, UUID standardRouteId, RouteStopStudentsRequestDTO routeStopStudentsRequestDTO) {
        UserModel authenticatedUser = userRepository.findUserByEmail(authenticatedEmail);

        if (authenticatedUser == null) throw new EntityNotFoundException("Usuário com o email " + authenticatedEmail + " não encontrado");

        StandardRoute standardRoute = standardRouteRepository.findById(standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException("Rota Padrão não encontrada: " + standardRouteId));

        UUID customerId = authenticatedUser.getCustomerId();

        // verifica se o user é válido (estudante, admin, platform_admin)
        checkValidUser(authenticatedUser);

        // valida mesmo Customer
        validateSameCustomer(customerId, standardRoute.getCustomerId());

        UUID studentIdFromDTO = routeStopStudentsRequestDTO.studentId();
        TravelPeriod travelPeriodFromDTO = routeStopStudentsRequestDTO.travelPeriod();

        StudentRouteStopAssignment assignment = studentRouteStopAssignmentRepository.findAssignmentByStudentRouteAndPeriod(studentIdFromDTO, standardRouteId, travelPeriodFromDTO, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Nenhum ponto de parada para o estudante: " + studentIdFromDTO + ", período: " + travelPeriodFromDTO + " e rota padrão: " + standardRouteId));

        Set<UUID> studentIds = resolveStudentIds(assignment.getRouteStop().getId(), standardRoute.getId());

        return studentRouteStopResponseMapper.toDTO(assignment, studentIds);
    }

    @Transactional
    public StudentRouteStopAssociateResponseDTO associateStudentWithRouteStop(String authenticatedEmail, UUID routeStopId, UUID standardRouteId, RouteStopStudentsRequestDTO routeStopStudentsRequestDTO) {
        UserModel authenticatedUser = userRepository.findUserByEmail(authenticatedEmail);

        if (authenticatedUser == null) throw new EntityNotFoundException("Usuário com o email " + authenticatedEmail + " não encontrado");

        checkValidUser(authenticatedUser);

        UUID studentIdFromDTO = routeStopStudentsRequestDTO.studentId();
        TravelPeriod travelPeriodByStudent = routeStopStudentsRequestDTO.travelPeriod();

        if (studentIdFromDTO == null) {
            throw new IllegalArgumentException("Nenhum estudante inserido");
        }

        if (studentRouteStopAssignmentRepository.countByStudentId(studentIdFromDTO) >= 3) {
            throw new DomainValidationException("Estudante: " + studentIdFromDTO + " já atingiu o limite máximo de 3 pontos de parada");
        }

        if (studentRouteStopAssignmentRepository.existsByStudentIdAndStandardRouteTravelPeriods(studentIdFromDTO, travelPeriodByStudent)) {
            throw new IllegalArgumentException("Estudante " + studentIdFromDTO + " já possui ponto no turno: " + travelPeriodByStudent);
        }

        Student student = studentRepository.findById(studentIdFromDTO)
                .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado: " + studentIdFromDTO));

        if (student.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new InactiveAccountException("Estudante Inativo no sistema: " + student.getId());
        }

        RouteStop routeStop = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new EntityNotFoundException("RouteStop não encontrado: " + routeStopId));

        if (routeStop.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new IllegalArgumentException("Ponto de Parada está INATIVO no sistema: " + routeStopId);
        }

        StandardRoute standardRoute = standardRouteRepository.findById(standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException("Rota Padrão não encontrada: " + standardRouteId));


        if (standardRoute.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new IllegalArgumentException("Rota padrão está INATIVA no sistema: " + standardRouteId);
        }

        if (standardRoute.getTravelPeriods().stream().noneMatch(period -> period.equals(travelPeriodByStudent))) {
            throw new DomainValidationException("O período informado (" + travelPeriodByStudent + ") não corresponde aos períodos da Rota Padrão");
        }

        UUID customerId = authenticatedUser.getCustomerId(); // customer base = usuário autenticado

        validateSameCustomer(customerId, routeStop.getCustomerId());
        validateSameCustomer(customerId, standardRoute.getCustomerId());
        validateSameCustomer(customerId, student.getCustomerId());

        boolean isAssignmentWithRouteStop = standardRoute.getRouteStopAssignments().stream()
                .anyMatch(assignment -> assignment.getRouteStop().getId().equals(routeStopId));

        if (!isAssignmentWithRouteStop) {
            throw new EntityAssignmentNotFound("Ponto de Parada: " + routeStopId + " não faz parte da Rota Padrão: " + standardRouteId);
        }

        StudentRouteStopAssignment studentRouteStopAssignment = new StudentRouteStopAssignment();
        studentRouteStopAssignment.setStudent(student);
        studentRouteStopAssignment.setRouteStop(routeStop);
        studentRouteStopAssignment.setStandardRoute(standardRoute);
        studentRouteStopAssignment.setTravelPeriod(travelPeriodByStudent);

        studentRouteStopAssignmentRepository.save(studentRouteStopAssignment);

        Set<UUID> studentIds = resolveStudentIds(routeStop.getId(), standardRoute.getId());

        return studentRouteStopResponseMapper.toDTO(studentRouteStopAssignment, studentIds);
    }

    @Transactional
    public StudentRouteStopAssociateResponseDTO updateStudentRouteStops(String authenticatedEmail, UUID studentId, UUID standardRouteId, RouteStopStudentUpdateDTO routeStopStudentUpdateDTO) {
        UserModel authenticatedUser = userRepository.findUserByEmail(authenticatedEmail);

        if (authenticatedUser == null) throw new EntityNotFoundException("Usuário com o email " + authenticatedEmail + " não encontrado");

        checkValidUser(authenticatedUser);

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado: " + studentId));

        UUID customerId = authenticatedUser.getCustomerId();
        validateSameCustomer(customerId, student.getCustomerId());

        StandardRoute standardRoute = standardRouteRepository.findById(standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException("Rota Padrão não encontrada: " + standardRouteId));

        validateSameCustomer(customerId, standardRoute.getCustomerId());

        if (standardRoute.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new IllegalArgumentException("Rota padrão está INATIVA no sistema: " + standardRouteId);
        }

        TravelPeriod travelPeriodFromDTO = routeStopStudentUpdateDTO.travelPeriod();
        if (standardRoute.getTravelPeriods().stream().noneMatch(period -> period.equals(travelPeriodFromDTO))) {
            throw new IllegalArgumentException("O período informado não corresponde ao período da Rota Padrão");
        }

        UUID newRouteStopId = routeStopStudentUpdateDTO.routeStopId();

        RouteStop newRouteStop = routeStopRepository.findById(newRouteStopId)
                .orElseThrow(() -> new EntityNotFoundException("Ponto de Parada não encontrado: " + newRouteStopId));

        validateSameCustomer(customerId, newRouteStop.getCustomerId());

        if (newRouteStop.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new IllegalArgumentException("Ponto de Parada está INATIVO no sistema: " + newRouteStopId);
        }

        // verifica se o novo ponto de parada pertence a esta rota padrão
        boolean belongsToRoute = standardRoute.getRouteStopAssignments().stream()
                .anyMatch(assignment -> assignment.getRouteStop().getId().equals(newRouteStopId));

        if (!belongsToRoute) {
            throw new EntityAssignmentNotFound("O Ponto de Parada não pertence à Rota Padrão informada");
        }

        // busca a associação atual para realizar a troca
        StudentRouteStopAssignment assignment = studentRouteStopAssignmentRepository
                .findByStudentIdAndStandardRouteId(studentId, standardRouteId)
                .orElseThrow(() -> new EntityAssignmentNotFound("Estudante sem vínculo ativo nesta Rota Padrão"));

        // verifica se JÁ EXISTE outro assignment (diferente do atual) para este estudante, nesta rota, neste turno
        boolean alreadyHasAssignmentInThisPeriod = studentRouteStopAssignmentRepository
                .existsByStudentIdAndStandardRouteIdAndTravelPeriodAndIdNot(
                        student.getId(),
                        standardRoute.getId(),
                        travelPeriodFromDTO,
                        assignment.getId()
                );

        if (alreadyHasAssignmentInThisPeriod) {
            throw new DomainValidationException("O estudante " + student.getId() + " já possui outro ponto de parada nesta rota no turno: " + travelPeriodFromDTO);
        }

        // atualiza o ponto de parada na associação
        assignment.setRouteStop(newRouteStop);
        studentRouteStopAssignmentRepository.save(assignment);

        Set<UUID> studentIds = resolveStudentIds(assignment.getRouteStop().getId(), standardRoute.getId());

        return studentRouteStopResponseMapper.toDTO(assignment, studentIds);
    }

    @Transactional
    public StudentRouteStopAssociateResponseDTO removeStudentFromRouteStop(String authenticatedEmail, UUID routeStopId, UUID standardRouteId, RouteStopStudentsRequestDTO routeStopStudentsRequestDTO) {
        UserModel authenticatedUser = userRepository.findUserByEmail(authenticatedEmail);

        if (authenticatedUser == null) throw new EntityNotFoundException("Usuário com o email " + authenticatedEmail + " não encontrado");

        checkValidUser(authenticatedUser);

        UUID studentId = routeStopStudentsRequestDTO.studentId();

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Estudante não encontrado: " + studentId));

        RouteStop routeStop = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new EntityNotFoundException("RouteStop não encontrado: " + routeStopId));

        StandardRoute standardRoute = standardRouteRepository.findById(standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException("Rota Padrão não encontrada: " + standardRouteId));

        UUID customerId = authenticatedUser.getCustomerId();

        validateSameCustomer(customerId, student.getCustomerId());
        validateSameCustomer(customerId, routeStop.getCustomerId());
        validateSameCustomer(customerId, standardRoute.getCustomerId());

        if (routeStop.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new IllegalArgumentException("Ponto de Parada está INATIVO no sistema: " + routeStopId);
        }

        if (standardRoute.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new IllegalArgumentException("Rota padrão está INATIVA no sistema: " + standardRouteId);
        }

        // verifica se o ponto de parada pertence a esta rota padrão
        boolean belongsToRoute = standardRoute.getRouteStopAssignments().stream()
                .anyMatch(assignment -> assignment.getRouteStop().getId().equals(routeStopId));

        if (!belongsToRoute) {
            throw new EntityAssignmentNotFound("Ponto de Parada: " + routeStopId + " não faz parte da Rota Padrão: " + standardRouteId);
        }

        TravelPeriod travelPeriodFromDTO = routeStopStudentsRequestDTO.travelPeriod();

        if (standardRoute.getTravelPeriods().stream().noneMatch(period -> period.equals(travelPeriodFromDTO))) {
            throw new DomainValidationException("O período informado (" + travelPeriodFromDTO + ") não corresponde ao período da Rota Padrão");
        }

        StudentRouteStopAssignment assignment = studentRouteStopAssignmentRepository
                .findByStudentIdAndStandardRouteIdAndRouteStopId(studentId, standardRouteId, routeStopId)
                .orElseThrow(() -> new EntityAssignmentNotFound("O estudante " + studentId + " não possui vínculo com o Ponto de Parada " + routeStopId + " nesta Rota Padrão."));

        // remoção agora é direta no repositório da entidade de relacionamento
        studentRouteStopAssignmentRepository.delete(assignment);

        Set<UUID> studentIds = resolveStudentIds(assignment.getRouteStop().getId(), standardRoute.getId());

        return studentRouteStopResponseMapper.toDTO(assignment, studentIds);
    }

    // MÉTODOS AUXILIARES
    private void checkValidUser(UserModel authenticatedUser) {
        boolean isAdmin = authenticatedUser.getRoles().stream()
                .anyMatch(role -> role.equals("ROLE_ADMIN") || role.equals("ROLE_PLATFORM_ADMIN"));

        boolean isStudent = authenticatedUser.getRoles().stream()
                .anyMatch(role -> role.equals("ROLE_USER"));

        if (!(isAdmin || isStudent)) {
            throw new NotAuthorizedException("Apenas Estudantes ou Administradores podem realizar ações de Rotas Padrão");
        }

        if (authenticatedUser.getCustomerId() == null) throw new DomainValidationException("O usuário autenticado não está associado a um Customer");
        if (authenticatedUser.getStatus().equals(GeneralStatus.INACTIVE)) throw new InactiveAccountModificationException("Usuário não está ativo");
    }

    private void validateSameCustomer(UUID firstCustomerId, UUID secondCustomerId) {
        if (firstCustomerId == null || secondCustomerId == null ||
                !firstCustomerId.equals(secondCustomerId)) {
            throw new CustomerMismatchException("Os recursos não pertencem ao mesmo Customer: " + "first: " + firstCustomerId + ", second: " + secondCustomerId);
        }
    }

    private Set<UUID> resolveStudentIds(UUID routeStopId, UUID standardRouteId) {
        return studentRouteStopAssignmentRepository
                .findByRouteStopIdAndStandardRouteId(routeStopId, standardRouteId).stream()
                .map(a -> a.getStudent().getId())
                .collect(Collectors.toSet());
    }
}
