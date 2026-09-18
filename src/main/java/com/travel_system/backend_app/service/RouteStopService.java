package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.infrastructure.TenantContext;
import com.travel_system.backend_app.interfaces.mappers.RouteStopRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.RouteStopResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.*;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

import static com.travel_system.backend_app.service.CurrentUserService.getAuthenticatedUserEmail;

@Service
public class RouteStopService {

    private final UserAccountRepository userAccountRepository;
    private final RouteStopRepository routeStopRepository;
    private final StudentRepository studentRepository;
    private final StudentRouteStopAssignmentRepository studentRouteStopAssignmentRepository;
    private final AdministratorRepository administratorRepository;

    private final RouteStopResponseMapper routeStopResponseMapper;
    private final RouteStopRequestMapper routeStopRequestMapper;

    public RouteStopService(UserAccountRepository userAccountRepository, RouteStopRepository routeStopRepository, StudentRepository studentRepository, StudentRouteStopAssignmentRepository studentRouteStopAssignmentRepository, AdministratorRepository administratorRepository, RouteStopResponseMapper routeStopResponseMapper, RouteStopRequestMapper routeStopRequestMapper) {
        this.userAccountRepository = userAccountRepository;
        this.routeStopRepository = routeStopRepository;
        this.studentRepository = studentRepository;
        this.studentRouteStopAssignmentRepository = studentRouteStopAssignmentRepository;
        this.administratorRepository = administratorRepository;
        this.routeStopResponseMapper = routeStopResponseMapper;
        this.routeStopRequestMapper = routeStopRequestMapper;
    }

    @Transactional(readOnly = true)
    public RouteStopResponseDTO getRouteStopByName(String routeName) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Student student = getAndValidateActiveStudent(authenticatedUserEmail);

        RouteStop routeStop = routeStopRepository.findByName(routeName)
                .orElseThrow(() -> new EntityNotFoundException("RouteStop não encontrado pelo nome: " + routeName));

        validateSameCustomer(student.getCustomerId(), routeStop.getCustomerId());

        return routeStopResponseMapper.toDTO(routeStop);
    }

    @Transactional(readOnly = true)
    public RouteStopResponseDTO getRouteStopById(UUID routeStopId) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        Student student = getAndValidateActiveStudent(authenticatedUserEmail);

        RouteStop routeStop = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new EntityNotFoundException("RouteStop não encontrado: " + routeStopId));

        validateSameCustomer(student.getCustomerId(), routeStop.getCustomerId());

        return routeStopResponseMapper.toDTO(routeStop);
    }

    @Transactional
    public RouteStopResponseDTO createRouteStop(RouteStopRequestDTO routeStopRequestDTO) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        // verifica se é um admin válido
        checkValidAdmin(authenticatedUserEmail);

        // obtém o customerID do contexto atual e valida existência
        UUID customerId = TenantContext.getCurrentTenant();
        if (customerId == null ) {
            throw new DomainValidationException("É necessário estar atuando sobre um Customer válido");
        }

        // valida duplicidade do nome do RouteStop no customer
        if (routeStopRepository.existsByNameAndCustomerId(routeStopRequestDTO.name(), customerId)) {
            throw new DuplicateResourceException("Já existe um RouteStop com esse nome: " + routeStopRequestDTO.name());
        }

        // mapeia o salva o routestop
        RouteStop routeStop = routeStopRequestMapper.toEntity(routeStopRequestDTO);// mapper DTO converte para entidade
        routeStop.setStatus(GeneralStatus.ACTIVE);

        RouteStop savedRouteStop = routeStopRepository.save(routeStop);

        return routeStopResponseMapper.toDTO(savedRouteStop);
    }

    @Transactional
    public RouteStopResponseDTO updateRouteStop(UUID routeStopId, RouteStopUpdateDTO routeStopUpdateDTO) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        RouteStop routeStop = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new EntityNotFoundException("RouteStop não encontrado: " + routeStopId));

        // verifica se o administrador é válido
        checkValidAdmin(authenticatedUserEmail);

        // obtém o customerID do contexto atual e valida existência
        UUID customerId = TenantContext.getCurrentTenant();
        if (customerId == null ) {
            throw new DomainValidationException("É necessário estar atuando sobre um Customer válido");
        }

        // valida mesmo Customer
        validateSameCustomer(customerId, routeStop.getCustomerId());

        // valida duplicidade do nome do RouteStop no customer
        if (routeStopUpdateDTO.name() != null && !routeStopUpdateDTO.name().equals(routeStop.getName())) {
            if (routeStopRepository.existsByNameAndCustomerIdAndIdNot(routeStopUpdateDTO.name(), customerId, routeStopId)) {
                throw new DuplicateResourceException("Já existe um RouteStop com esse nome neste Customer: " + routeStopUpdateDTO.name());
            }
        }

        boolean hasLatitude = routeStopUpdateDTO.latitude() != null;
        boolean hasLongitude = routeStopUpdateDTO.longitude() != null;

        // verifica se informou ambas as coordenadas de origem
        if (hasLatitude != hasLongitude) {
            throw new NoSuchCoordinates("As coordenadas de Latitude e Longitude da origem devem ser informadas juntas");
        }

        routeStopRequestMapper.routeStopUpdateDTO(routeStopUpdateDTO, routeStop); // mapper DTO converte para entidade

        RouteStop savedRouteStop = routeStopRepository.save(routeStop);

        return routeStopResponseMapper.toDTO(savedRouteStop);
    }

    @Transactional
    public void updateRouteStopStatus(UUID routeStopId, GeneralStatus status) {
        String authenticatedUserEmail = getAuthenticatedUserEmail();

        checkValidAdmin(authenticatedUserEmail);

        RouteStop routeStop = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new EntityNotFoundException("RouteStop não encontrado"));

        // obtém o customerID do contexto atual e valida existência
        UUID customerId = TenantContext.getCurrentTenant();
        if (customerId == null ) {
            throw new DomainValidationException("É necessário estar atuando sobre um Customer válido");
        }

        validateSameCustomer(customerId, routeStop.getCustomerId());

        if (routeStop.getStatus() == status) {
            throw new IllegalStateException("RouteStop já contém o status " + status);
        }

        routeStop.setStatus(status);

        routeStopRepository.save(routeStop);
    }

    private void checkValidAdmin(String email) {
        // realiza a validação p/ ver se o Filter do Spring Security conseguiu associar o Tenant (seja pelo JWT ou as act)
        if (TenantContext.getCurrentTenant() == null) {
            throw new DomainValidationException("O usuário autenticado não está associado a um Customer nesta requisição.");
        }

        Administrator admin = administratorRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Perfil de Administrador não encontrado para este usuário."));

        if (admin.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountModificationException("Usuário não está ativo.");
        }
    }

    private Student getAndValidateActiveStudent(String email) {
        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Email " + email + " não encontrado para nenhum estudante no sistema"));

        if (student.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountException("Não é possível realizar operações em contas inativas no sistema");
        }

        return student;
    }

    private void validateSameCustomer(UUID firstCustomerId, UUID secondCustomerId) {
        if (firstCustomerId == null || secondCustomerId == null || !firstCustomerId.equals(secondCustomerId)) {
            throw new CustomerMismatchException("Os recursos não pertencem ao mesmo Customer.");
        }
    }
}
