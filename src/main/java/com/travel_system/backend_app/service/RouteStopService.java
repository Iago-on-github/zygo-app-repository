package com.travel_system.backend_app.service;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.RouteStopRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.RouteStopResponseMapper;
import com.travel_system.backend_app.interfaces.mappers.StandardRouteRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.StandardRouteResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.request.*;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.RouteStopRepository;
import com.travel_system.backend_app.repository.StandardRouteRepository;
import com.travel_system.backend_app.repository.StudentRepository;
import com.travel_system.backend_app.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RouteStopService {

    private final UserRepository userRepository;
    private final RouteStopRepository routeStopRepository;
    private final StudentRepository studentRepository;

    private final MapboxAPIService mapboxAPIService;

    private final RouteStopResponseMapper routeStopResponseMapper;
    private final RouteStopRequestMapper routeStopRequestMapper;

    public RouteStopService(UserRepository userRepository, RouteStopRepository routeStopRepository, StudentRepository studentRepository, MapboxAPIService mapboxAPIService, RouteStopResponseMapper routeStopResponseMapper, RouteStopRequestMapper routeStopRequestMapper) {
        this.userRepository = userRepository;
        this.routeStopRepository = routeStopRepository;
        this.studentRepository = studentRepository;
        this.mapboxAPIService = mapboxAPIService;
        this.routeStopResponseMapper = routeStopResponseMapper;
        this.routeStopRequestMapper = routeStopRequestMapper;
    }

    @Transactional(readOnly = true)
    public List<RouteStopResponseDTO> getRouteStopsByCustomer(UUID customerId) {
        List<RouteStop> routeStopsByCustomerId = routeStopRepository.findRouteStopsByCustomerId(customerId);

        return routeStopsByCustomerId.stream().map(routeStopResponseMapper::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public RouteStopResponseDTO getRouteStopByName(String routeName) {
        RouteStop routeStop = routeStopRepository.findByName(routeName)
                .orElseThrow(() -> new EntityNotFoundException("RouteStop não encontrado pelo nome: " + routeName));

        return routeStopResponseMapper.toDTO(routeStop);
    }

    @Transactional(readOnly = true)
    public RouteStopResponseDTO getRouteStopById(UUID routeStopId) {
        RouteStop routeStop = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new EntityNotFoundException("RouteStop não encontrado: " + routeStopId));

        return routeStopResponseMapper.toDTO(routeStop);
    }

    @Transactional
    public RouteStopResponseDTO createRouteStop(String authenticatedEmail, RouteStopRequestDTO routeStopRequestDTO) {
        UserModel authenticatedUser = userRepository.findUserByEmail(authenticatedEmail);

        if (authenticatedUser == null) throw new EntityNotFoundException("Usuário com o email " + authenticatedEmail + " não encontrado");

        // verifica se o user é válido (admin, platform_admin)
        checkValidAdmin(authenticatedUser);
        checkAdminPrivileges(authenticatedUser);

        RouteStop routeStop = routeStopRequestMapper.toEntity(routeStopRequestDTO);// mapper DTO converte para entidade

        // valida mesmo Customer
        validateSameCustomer(authenticatedUser.getCustomer().getId(), routeStop.getCustomer().getId());

        // duplicidade de "name"
        boolean isAlreadyExistsRouteStopName = routeStopRepository.findByNameAndCustomerId(routeStopRequestDTO.name(), authenticatedUser.getCustomer().getId());

        if (isAlreadyExistsRouteStopName) throw new DuplicateResourceException("Já existe um RouteStop com esse nome: " + routeStopRequestDTO.name());

        routeStop.setCustomer(authenticatedUser.getCustomer()); // mesmo customer do user autenticado
        routeStop.setCreatedAt(Instant.now());
        routeStop.setStatus(GeneralStatus.ACTIVE);

        // opcional: adicionar estudantes enquanto criar a rota
        if (!routeStopRequestDTO.studentIds().isEmpty()) {
            Set<UUID> studentIds = routeStopRequestDTO.studentIds();

            List<Student> students = studentRepository.findAllById(studentIds);

            for (Student student : students) {
                if (student == null) throw new EntityNotFoundException("Algum dos estudantes não foi encontrado");
                if (student.getStatus().equals(GeneralStatus.INACTIVE)) throw new InactiveAccountException("Estudante Inativo no sistema: " + student.getId());

                // devem ser do mesmo customer
                validateSameCustomer(authenticatedUser.getCustomer().getId(), student.getCustomer().getId());

                routeStop.getStudents().add(student);
            }
        }

        RouteStop savedRouteStop = routeStopRepository.save(routeStop);

        return routeStopResponseMapper.toDTO(savedRouteStop);
    }

    @Transactional
    public RouteStopResponseDTO updateRouteStop(String authenticatedEmail, UUID routeStopId, RouteStopUpdateDTO routeStopUpdateDTO) {
        UserModel authenticatedUser = userRepository.findUserByEmail(authenticatedEmail);

        RouteStop routeStop = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new EntityNotFoundException("RouteStop não encontrado: " + routeStopId));

        if (authenticatedUser == null) throw new EntityNotFoundException("Usuário com o email " + authenticatedEmail + " não encontrado");

        // verifica se o user é válido (admin, platform_admin)
        checkValidAdmin(authenticatedUser);
        checkAdminPrivileges(authenticatedUser);

        // valida mesmo Customer
        validateSameCustomer(authenticatedUser.getCustomer().getId(), routeStop.getCustomer().getId());

        // duplicidade de "name"
        boolean isAlreadyExistsRouteStopName = routeStopRepository.findByNameAndCustomerId(routeStopUpdateDTO.name(), authenticatedUser.getCustomer().getId());

        if (isAlreadyExistsRouteStopName) throw new DuplicateResourceException("Já existe um RouteStop com esse nome: " + routeStopUpdateDTO.name());

        boolean routeStopLatitude = routeStopUpdateDTO.latitude() != null;
        boolean routeStopLongitude = routeStopUpdateDTO.longitude() != null;

        // verifica se informou ambas as coordenadas de origem
        if (routeStopLatitude != routeStopLongitude) {
            throw new NoSuchCoordinates("As coordenadas de Latitude e Longitude da origem devem ser informadas juntas");
        }

        routeStop.setLatitude(routeStopUpdateDTO.latitude());
        routeStop.setLongitude(routeStopUpdateDTO.longitude());

        routeStop.setUpdatedAt(Instant.now());

        routeStopRequestMapper.routeStopUpdateDTO(routeStopUpdateDTO);// mapper DTO converte para entidade

        RouteStop savedRouteStop = routeStopRepository.save(routeStop);

        return routeStopResponseMapper.toDTO(savedRouteStop);
    }

    @Transactional
    public RouteStopResponseDTO addStudentsToRouteStop(String authenticatedEmail, UUID routeStopId, RouteStopStudentsRequestDTO routeStopStudentsRequestDTO) {
        UserModel authenticatedUser = userRepository.findUserByEmail(authenticatedEmail);

        RouteStop routeStop = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new EntityNotFoundException("RouteStop não encontrado: " + routeStopId));

        if (authenticatedUser == null) throw new EntityNotFoundException("Usuário com o email " + authenticatedEmail + " não encontrado");

        // verifica se o user é válido (admin, platform_admin)
        checkValidAdmin(authenticatedUser);
        checkAdminPrivileges(authenticatedUser);

        // valida mesmo Customer
        validateSameCustomer(authenticatedUser.getCustomer().getId(), routeStop.getCustomer().getId());

        if (routeStopStudentsRequestDTO.studentIds().isEmpty()) {
            throw new IllegalArgumentException("Nenhum estudante inserido");
        }

        List<Student> students = studentRepository.findAllById(routeStopStudentsRequestDTO.studentIds());

        for (Student student : students) {
            if (student == null) throw new EntityNotFoundException("Algum estudante inserido não foi encontrado");
            if (student.getStatus().equals(GeneralStatus.INACTIVE)) throw new InactiveAccountException("Estudante Inativo no sistema: " + student.getId());

            // valida mesmo customer
            validateSameCustomer(authenticatedUser.getCustomer().getId(), student.getCustomer().getId());

            // insere os novos estudantes
            routeStop.getStudents().add(student);
        }

        routeStop.setUpdatedAt(Instant.now());

        RouteStop savedRouteStop = routeStopRepository.save(routeStop);

        return routeStopResponseMapper.toDTO(savedRouteStop);
    }

    @Transactional
    public RouteStopResponseDTO removeStudentToRouteStop(String authenticatedEmail, UUID routeStopId, RouteStopStudentsRequestDTO routeStopStudentsRequestDTO) {
        UserModel authenticatedUser = userRepository.findUserByEmail(authenticatedEmail);

        RouteStop routeStop = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new EntityNotFoundException("RouteStop não encontrado: " + routeStopId));

        if (authenticatedUser == null) throw new EntityNotFoundException("Usuário com o email " + authenticatedEmail + " não encontrado");

        // verifica se o user é válido (admin, platform_admin)
        checkValidAdmin(authenticatedUser);
        checkAdminPrivileges(authenticatedUser);

        // valida mesmo Customer
        validateSameCustomer(authenticatedUser.getCustomer().getId(), routeStop.getCustomer().getId());

        if (routeStop.getStudents().isEmpty()) {
            throw new IllegalArgumentException("Nenhum estudante vinculado ao RouteStop");
        }

        Set<UUID> studentIdsToRemove = routeStopStudentsRequestDTO.studentIds();

        List<UUID> studentIds = routeStop.getStudents().stream().map(Student::getId).toList();

        List<Student> students = studentRepository.findAllById(studentIds);

        for (Student student : students) {
            // valida mesmo customer
            validateSameCustomer(authenticatedUser.getCustomer().getId(), student.getCustomer().getId());

            // remove os estudantes que forem procedentes com os ids
            for (UUID studentIdToRemove : studentIdsToRemove) {
                routeStop.getStudents().removeIf(stId -> stId.getId().equals(studentIdToRemove));
            }
        }

        routeStop.setUpdatedAt(Instant.now());

        RouteStop savedRouteStop = routeStopRepository.save(routeStop);

        return routeStopResponseMapper.toDTO(savedRouteStop);
    }

    @Transactional
    public void updateRouteStopStatus(UUID routeStopId, String authenticatedEmail, GeneralStatus status) {
        UserModel authenticatedUser = userRepository.findUserByEmail(authenticatedEmail);

        if (authenticatedUser == null) throw new EntityNotFoundException("Usuário com o email " + authenticatedEmail + " não encontrado");

        // verifica se é um ADMIN
        checkAdminPrivileges(authenticatedUser);
        checkValidAdmin(authenticatedUser); // verifca se o user é válido (status, customer existe)

        RouteStop routeStop = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new EntityNotFoundException("RouteStop não encontrado"));

        validateSameCustomer(routeStop.getCustomer().getId(), authenticatedUser.getCustomer().getId());

        if (routeStop.getStatus() == status) {
            throw new DuplicateResourceException("RouteStop já contém o status " + status);
        }

        routeStop.setStatus(status);

        routeStopRepository.save(routeStop);
    }


    // MÉTODOS AUXILIARES
    private List<Point> buildWaypoints(List<RouteStopAssignment> assignmentsOrderedBySequence) {
        return assignmentsOrderedBySequence.stream().map(route -> {
            RouteStop eachRouteStop = route.getRouteStop();

            if (eachRouteStop.getLongitude() == null || eachRouteStop.getLatitude() == null) {
                throw new DomainValidationException("O RouteStop " + eachRouteStop.getId() + " não possui coordenadas válidas");
            }

            return Point.fromLngLat(eachRouteStop.getLongitude(), eachRouteStop.getLatitude());
        }).toList();
    }

    private RouteDetailsDTO calculateStandardRouteGeometry(Double originLongitude, Double originLatitude, Double destinationLongitude, Double destinationLatitude, List<Point> waypoints) {
        if (originLongitude == null || originLatitude == null || destinationLongitude == null || destinationLatitude == null) {
            throw new IllegalArgumentException("Coordenadas da rota padrão inválidas ou inexistentes");
        }

        RouteDetailsDTO routeDetailsDTO = mapboxAPIService.calculateStandardRoute(
                originLongitude,
                originLatitude,
                destinationLongitude,
                destinationLatitude,
                waypoints);

        System.out.println("routeDetailsDTO: " + routeDetailsDTO);

        if (routeDetailsDTO == null || routeDetailsDTO.geometry() == null) {
            throw new RecalculateEtaException("Não foi possível recalcular a geometria da rota padrão");
        }

        return routeDetailsDTO;
    }

    private void checkAdminPrivileges(UserModel authenticatedUser) {
        boolean isAdmin = authenticatedUser.getRoles().stream()
                .anyMatch(role -> role.equals("ROLE_ADMIN") || role.equals("ROLE_PLATFORM_ADMIN"));

        if (!isAdmin) {
            throw new NotAuthorizedException("Apenas Administradores e Administradores de Plataforma podem criar ou modificar rotas");
        }
    }

    private void checkValidAdmin(UserModel authenticatedUser) {
        if (authenticatedUser.getCustomer() == null) throw new DomainValidationException("O usuário autenticado não está associado a um Customer");
        if (authenticatedUser.getStatus().equals(GeneralStatus.INACTIVE)) throw new InactiveAccountModificationException("Usuário não está ativo");
    }

    private void validateSameCustomer(UUID firstCustomerId, UUID secondCustomerId) {
        if (firstCustomerId == null || secondCustomerId == null ||
                !firstCustomerId.equals(secondCustomerId)) {
            throw new CustomerMismatchException("Os recursos não pertencem ao mesmo Customer: " + "first: " + firstCustomerId + ", second: " + secondCustomerId);
        }
    }
}
