package com.travel_system.backend_app.service;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.StandardRouteRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.StandardRouteResponseMapper;
import com.travel_system.backend_app.model.RouteStop;
import com.travel_system.backend_app.model.RouteStopAssignment;
import com.travel_system.backend_app.model.StandardRoute;
import com.travel_system.backend_app.model.UserModel;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.request.*;
import com.travel_system.backend_app.model.dtos.response.RouteStopAssignmentResponseDTO;
import com.travel_system.backend_app.model.dtos.response.RouteStopResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.repository.RouteStopRepository;
import com.travel_system.backend_app.repository.StandardRouteRepository;
import com.travel_system.backend_app.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StandardRouteService {
    private final StandardRouteRepository standardRouteRepository;
    private final RouteStopRepository routeStopRepository;
    private final UserRepository userRepository;

    private final StandardRouteRequestMapper standardRouteRequestMapper;
    private final StandardRouteResponseMapper standardRouteResponseMapper;

    private final CurrentUserService currentUserService;
    private final MapboxAPIService mapboxAPIService;

    public StandardRouteService(StandardRouteRepository standardRouteRepository, RouteStopRepository routeStopRepository, UserRepository userRepository, StandardRouteRequestMapper standardRouteRequestMapper, StandardRouteResponseMapper standardRouteResponseMapper, CurrentUserService currentUserService, MapboxAPIService mapboxAPIService) {
        this.standardRouteRepository = standardRouteRepository;
        this.routeStopRepository = routeStopRepository;
        this.userRepository = userRepository;
        this.standardRouteRequestMapper = standardRouteRequestMapper;
        this.standardRouteResponseMapper = standardRouteResponseMapper;
        this.currentUserService = currentUserService;
        this.mapboxAPIService = mapboxAPIService;
    }

    @Transactional(readOnly = true)
    public Page<StandardRouteResponseDTO> getAllStandardRoutes() {
        // permitido apenas caso seja platformADM
        if (!currentUserService.isPlatformAdmin()) {
            throw new NotAuthorizedException("Apenas administradores da plataforma podem realizar essa consulta");
        }

        Pageable pageable = PageRequest.of(0, 10);

        Page<StandardRoute> allStandardRoutes = standardRouteRepository.findAll(pageable);

        return allStandardRoutes.map(standardRouteResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public StandardRouteResponseDTO getStandardRouteById(UUID standardRouteId) {
        StandardRoute standardRoute = standardRouteRepository.findById(standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException("Rota padrão " + standardRouteId + " não encontrada"));

        return standardRouteResponseMapper.toDTO(standardRoute);
    }

    @Transactional(readOnly = true)
    public Page<StandardRouteResponseDTO> getAllStandardRouteByCustomer(UUID customerId) {
        // permitido apenas caso seja platformADM
        if (!currentUserService.isPlatformAdmin()) {
            throw new NotAuthorizedException("Apenas administradores da plataforma podem realizar essa consulta");
        }

        Pageable pageable = PageRequest.of(0, 10);

        Page<StandardRoute> standardRoutesPage = standardRouteRepository.findAllByCustomerId(customerId, pageable);

        return standardRoutesPage.map(standardRouteResponseMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public StandardRouteResponseDTO getStandardRouteStopPoints(UUID standardRouteId, GeneralStatus status) {
        StandardRoute baseRoute = standardRouteRepository.findRouteBaseByIdAndStatus(standardRouteId, status)
                .orElseThrow(() -> new EntityNotFoundException("Rota não encontrada"));

        Set<RouteStopAssignmentResponseDTO> stops = standardRouteRepository.findAssignmentsByRouteId(standardRouteId);

        // mapeamento manual pois precisa fazer a inserão das rotas buscadas em uma query diferente
        return new StandardRouteResponseDTO(
                baseRoute.getId(),
                baseRoute.getRouteName(),
                baseRoute.getRouteDescription(),
                baseRoute.getOriginLatitude(),
                baseRoute.getOriginLongitude(),
                baseRoute.getDestinationLatitude(),
                baseRoute.getDestinationLongitude(),
                baseRoute.getStandardGeometry(),
                baseRoute.getTravelPeriods(),
                stops, // routeStops
                baseRoute.getCustomer().getId(),
                baseRoute.getStatus(),
                baseRoute.getCreatedAt(),
                baseRoute.getUpdatedAt()
        );
    }

    @Transactional
    public StandardRouteResponseDTO createStandardRoute(String authenticatedEmail, StandardRouteRequestDTO standardRouteRequestDTO) {
        UserModel authenticatedUser = userRepository.findUserByEmail(authenticatedEmail);

        if (authenticatedUser == null) throw new EntityNotFoundException("Usuário com o email " + authenticatedEmail + " não encontrado");

        // verifica se é um admin
        checkAdminPrivileges(authenticatedUser);
        checkValidAdmin(authenticatedUser); // verifica se o user é válido

        if (standardRouteRequestDTO.routeStops().isEmpty()) {
            throw new DomainValidationException("A rota padrão deve possuir ao menos um ponto de parada");
        }

        StandardRoute standardRoute = standardRouteRequestMapper.toEntity(standardRouteRequestDTO); // mapper DTO -> entity

        boolean isDuplicatedName = standardRouteRepository.existsByRouteNameAndCustomerId(standardRouteRequestDTO.routeName(), authenticatedUser.getCustomer().getId());

        if (isDuplicatedName) throw new DuplicateResourceException("Já existe uma rota com o nome: " + standardRouteRequestDTO.routeName());

        // valida a ordem de parada não deixando ela se repetir (ex.: parada 1 (0), parada 4(0) e não deixando ser null
        List<Integer> stopSequence = standardRouteRequestDTO.routeStops().stream()
                .map(RouteStopAssignmentRequestDTO::stopSequence).toList();

        if (stopSequence.stream().anyMatch(Objects::isNull)) {
            throw new DomainValidationException("A sequência dos RouteStops não pode ser nula");
        }

        if (new HashSet<>(stopSequence).size() != stopSequence.size()) {
            throw new DomainValidationException("Não pode haver stopSequence duplicado");
        }

        // verifica valores inválidos
        if (stopSequence.stream().anyMatch(sequence -> sequence <= 0)) {
            throw new DomainValidationException("A sequência dos RouteStops deve ser maior que zero");
        }

        // IDs e ordenação dos pontos de parada respectivamente
        List<UUID> routeStopIds = standardRouteRequestDTO.routeStops().stream()
                .map(RouteStopAssignmentRequestDTO::routeStopId).toList();

        if (new HashSet<>(routeStopIds).size() != routeStopIds.size()) {
            throw new DomainValidationException("Um mesmo RouteStop não pode ser utilizado mais de uma vez na mesma rota");
        }

        standardRoute.setCustomer(authenticatedUser.getCustomer()); // deve ser o mesmo Customer do usuário autenticado

        List<RouteStop> routeStops = routeStopRepository.findAllById(routeStopIds);

        if (routeStops.isEmpty()) throw new EntityNotFoundException("Nenhum RouteStop encontrado");

        boolean isInactiveRouteStop = routeStops.stream()
                .anyMatch(routeStop -> routeStop.getStatus() == GeneralStatus.INACTIVE);

        if (isInactiveRouteStop) {
            throw new IllegalArgumentException("Não é possível criar um RouteStop inativo");
        }

        Map<UUID, RouteStop> routeStopsById = routeStops.stream()
                .collect(Collectors.toMap(
                        RouteStop::getId,
                        Function.identity()
                ));


        List<RouteStopAssignment> assignments = standardRouteRequestDTO.routeStops().stream()
                .map(request -> {
                    RouteStop routeStop = routeStopsById.get(request.routeStopId());

                    if (routeStop == null) throw new EntityNotFoundException("RouteStop não encontrado");

                    validateSameCustomer(
                            routeStop.getCustomer().getId(),
                            standardRoute.getCustomer().getId());

                    RouteStopAssignment assignment = new RouteStopAssignment();

                    assignment.setStandardRoute(standardRoute);
                    assignment.setRouteStop(routeStop);
                    assignment.setSequence(request.stopSequence());
                    assignment.setOptionalSpot(request.isOptionalStop());

                    return assignment;
                })
                .sorted(Comparator.comparing(RouteStopAssignment::getSequence))
                .toList();

        // constroi os waypoints
        List<Point> waypoints = buildWaypoints(assignments);

        // faz a consulta no mapbox buscando pelos dados de geometry padrão da rota
        RouteDetailsDTO routeDetailsDTO = calculateStandardRouteGeometry(standardRouteRequestDTO.originLongitude(),
                standardRouteRequestDTO.originLatitude(),
                standardRouteRequestDTO.destinationLongitude(),
                standardRouteRequestDTO.destinationLatitude(),
                waypoints);

        standardRoute.setStandardGeometry(routeDetailsDTO.geometry());

        // associa os pontos de parada à rota através dos assignments preservando a sequência oficial de cada ponto
        standardRoute.setRouteStopAssignments(assignments);
        standardRoute.setStatus(GeneralStatus.ACTIVE);
        standardRoute.setTravelPeriods(standardRouteRequestDTO.periods());
        standardRoute.setCreatedAt(Instant.now());

        // importante: caso o cascade do relacionamento seja removido é necessário salvar o RouteStopAssignments explicitamente
        StandardRoute savedStandardRoute = standardRouteRepository.save(standardRoute);


        return standardRouteResponseMapper.toDTO(savedStandardRoute);
    }

    @Transactional
    public StandardRouteResponseDTO updateStandardRoute(UUID standardRouteId, String authenticatedEmail, StandardRouteUpdateDTO standardRouteUpdateDTO) {
        UserModel authenticatedUser = userRepository.findUserByEmail(authenticatedEmail);

        if (authenticatedUser == null) throw new EntityNotFoundException("Usuário com o email " + authenticatedEmail + " não encontrado");

        // verifica se é um ADMIN
        checkAdminPrivileges(authenticatedUser);
        checkValidAdmin(authenticatedUser); // verifca se o user é válido (status, customer existe)

        boolean isDuplicatedName = standardRouteRepository.existsByRouteNameAndCustomerIdAndIdNot(standardRouteUpdateDTO.routeName(), authenticatedUser.getCustomer().getId(), standardRouteId);

        if (isDuplicatedName) throw new IllegalArgumentException("Já existe uma rota com o nome: " + standardRouteUpdateDTO.routeName());

        StandardRoute standardRoute = standardRouteRepository.findById(standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException("Rota padrão não encontrada: " + standardRouteId));

        // devem ser do mesmo Customer
        validateSameCustomer(standardRoute.getCustomer().getId(), authenticatedUser.getCustomer().getId());

        /*
        * valida o input das coordenadas de origem e destino
        * */
        boolean originLatitudeFromDTO = standardRouteUpdateDTO.originLatitude() != null;
        boolean originLongitudeFromDTO = standardRouteUpdateDTO.originLongitude() != null;

        // verifica se informou ambas as coordenadas de origem
        if (originLatitudeFromDTO != originLongitudeFromDTO) {
            throw new NoSuchCoordinates("As coordenadas de Latitude e Longitude da origem devem ser informadas juntas");
        }

        Double originLat = originLatitudeFromDTO ? standardRouteUpdateDTO.originLatitude() : standardRoute.getOriginLatitude();
        Double originLng = originLongitudeFromDTO ? standardRouteUpdateDTO.originLongitude() : standardRoute.getOriginLongitude();

        boolean destinationLatitudeFromDTO = standardRouteUpdateDTO.destinationLatitude() != null;
        boolean destinationLongitudeFromDTO = standardRouteUpdateDTO.destinationLongitude() != null;

        // verifica se informou ambas as coordenadas de destino
        if (destinationLatitudeFromDTO != destinationLongitudeFromDTO) {
            throw new NoSuchCoordinates("Latitude e longitude de destino devem ser informadas juntas");
        }

        Double destinationLat = destinationLatitudeFromDTO ? standardRouteUpdateDTO.destinationLatitude() : standardRoute.getDestinationLatitude();
        Double destinationLng = destinationLongitudeFromDTO ? standardRouteUpdateDTO.destinationLongitude() : standardRoute.getDestinationLongitude();

        List<RouteStopAssignment> routeStopAssignments = standardRoute.getRouteStopAssignments().stream()
                .sorted(Comparator.comparing(RouteStopAssignment::getSequence)).toList();

        // waypoints (pontos de parada) aqui não muda, busca pelo já armazenado
        List<Point> defaultWaypoints = buildWaypoints(routeStopAssignments);

        // faz a chamada pro cálculo do geomtry
        RouteDetailsDTO routeDetailsDTO = calculateStandardRouteGeometry(
                originLng,
                originLat,
                destinationLng,
                destinationLat,
                defaultWaypoints);

        // faz a inserção dos periods
        if (standardRouteUpdateDTO.periods() != null && !standardRouteUpdateDTO.periods().isEmpty()) {
            Set<TravelPeriod> newPeriods = new HashSet<>(standardRouteUpdateDTO.periods());

            standardRoute.setTravelPeriods(newPeriods);
        }

        standardRoute.setStandardGeometry(routeDetailsDTO.geometry());
        standardRoute.setUpdatedAt(Instant.now());

        // mapper para atualizar os campos
        standardRouteRequestMapper.standardRouteUpdateFromDTO(standardRouteUpdateDTO, standardRoute);

        StandardRoute savedStandardRoute = standardRouteRepository.save(standardRoute);

        return standardRouteResponseMapper.toDTO(savedStandardRoute);
    }

    @Transactional
    public StandardRouteResponseDTO updateRouteStopPoints(UUID standardRouteId, String authenticatedEmail, StandardRouteStopsUpdateDTO standardRouteStopsUpdateDTO) {
        UserModel authenticatedUser = userRepository.findUserByEmail(authenticatedEmail);

        if (authenticatedUser == null) throw new EntityNotFoundException("Usuário com o email " + authenticatedEmail + " não encontrado");

        // verifica se é um ADMIN
        checkAdminPrivileges(authenticatedUser);
        checkValidAdmin(authenticatedUser); // verifca se o user é válido (status, customer existe)

        StandardRoute standardRoute = standardRouteRepository.findById(standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException("Entidade standardRoute não encontrada"));

        validateSameCustomer(authenticatedUser.getCustomer().getId(), standardRoute.getCustomer().getId());

        if (standardRouteStopsUpdateDTO == null || standardRouteStopsUpdateDTO.routeStops() == null || standardRouteStopsUpdateDTO.routeStops().isEmpty()) {
            throw new DomainValidationException("A rota padrão deve possuir ao menos um ponto de parada");
        }

        List<RouteStopAssignmentRequestDTO> requestedStops = standardRouteStopsUpdateDTO.routeStops().stream().toList();

        List<Integer> sequence = requestedStops.stream().map(RouteStopAssignmentRequestDTO::stopSequence).toList();

        if (sequence.stream().anyMatch(Objects::isNull)) {
            throw new DomainValidationException("A sequência de RouteStops não pode ser null");
        }

        if (sequence.stream().anyMatch(value -> value <= 0)) {
            throw new DomainValidationException("A sequência de RouteStops deve ser maior que zero");
        }

        if (new HashSet<>(sequence).size() != sequence.size()) {
            throw new DomainValidationException("Não pode haver stopSequence duplicado");
        }

        List<UUID> routeStopIds = requestedStops.stream().map(RouteStopAssignmentRequestDTO::routeStopId).toList();

        if (routeStopIds.stream().anyMatch(Objects::isNull)) {
            throw new DomainValidationException("O RouteStop não pode possuir ID nulo");
        }

        if (new HashSet<>(routeStopIds).size() != routeStopIds.size()) {
            throw new DomainValidationException("Um mesmo RouteStop não pode ser utilizado mais de uma vez na mesma rota");
        }

        List<RouteStop> routeStops = routeStopRepository.findAllById(routeStopIds);

        if (routeStops.isEmpty()) {
            throw new EntityNotFoundException("Nenhum RouteStop encontrado");
        }

        boolean hasInactiveRouteStop = routeStops.stream()
                .anyMatch(routeStop -> routeStop.getStatus() == GeneralStatus.INACTIVE);

        if (hasInactiveRouteStop) {
            throw new IllegalArgumentException("Não é possível adicionar RouteStops inativos à rota padrão");
        }

        // indexa os RouteStops pelo ID para reconstruir os assignments
        Map<UUID, RouteStop> routeStopById = routeStops.stream()
                .collect(Collectors.toMap(
                        RouteStop::getId,
                        Function.identity() // próprio objeto
                ));

        // evita que os routeStops requisitados não venham
        if (routeStops.size() != routeStopIds.size()) {
            throw new EntityNotFoundException("Um ou mais RouteStops não foram encontrados");
        }

        for (RouteStop routeStop : routeStops) {
            validateSameCustomer(routeStop.getCustomer().getId(), authenticatedUser.getCustomer().getId());
        }

        // cria os novos RouteStopAssignments
        List<RouteStopAssignment> assignments = requestedStops.stream().map(request -> {
                    RouteStop routeStop = routeStopById.get(request.routeStopId());

                    RouteStopAssignment assignment = new RouteStopAssignment();

                    assignment.setStandardRoute(standardRoute);
                    assignment.setRouteStop(routeStop);
                    assignment.setSequence(request.stopSequence());
                    assignment.setOptionalSpot(request.isOptionalStop());

                    return assignment;
                }).sorted(Comparator.comparing(RouteStopAssignment::getSequence))
                .toList();

        // monta os waypoints
        List<Point> waypoints = buildWaypoints(assignments);

        RouteDetailsDTO routeDetailsDTO = calculateStandardRouteGeometry(standardRoute.getOriginLongitude(),
                standardRoute.getOriginLatitude(),
                standardRoute.getDestinationLongitude(),
                standardRoute.getDestinationLatitude(),
                waypoints);

        standardRoute.setStandardGeometry(routeDetailsDTO.geometry()); // armazena o geometry recalculado

        standardRoute.getRouteStopAssignments().clear(); // limpa os registros antigos (orphanRemoval da entidade)
        standardRoute.getRouteStopAssignments().addAll(assignments); // persiste através do cascade
        standardRoute.setUpdatedAt(Instant.now());

        StandardRoute savedStandardRoute = standardRouteRepository.save(standardRoute);

        // verificar retorno para routeStop
        return standardRouteResponseMapper.toDTO(savedStandardRoute);
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
