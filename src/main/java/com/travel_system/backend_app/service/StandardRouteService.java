package com.travel_system.backend_app.service;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.infrastructure.TenantContext;
import com.travel_system.backend_app.interfaces.mappers.StandardRouteRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.StandardRouteResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.request.*;
import com.travel_system.backend_app.model.dtos.response.RouteStopAssignmentResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.repository.AdministratorRepository;
import com.travel_system.backend_app.repository.RouteStopRepository;
import com.travel_system.backend_app.repository.StandardRouteRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StandardRouteService {
    private final StandardRouteRepository standardRouteRepository;
    private final RouteStopRepository routeStopRepository;
    private final UserAccountRepository userAccountRepository;
    private final AdministratorRepository administratorRepository;

    private final StandardRouteRequestMapper standardRouteRequestMapper;
    private final StandardRouteResponseMapper standardRouteResponseMapper;

    private final CurrentUserService currentUserService;
    private final MapboxAPIService mapboxAPIService;

    public StandardRouteService(StandardRouteRepository standardRouteRepository, RouteStopRepository routeStopRepository, UserAccountRepository userAccountRepository, AdministratorRepository administratorRepository, StandardRouteRequestMapper standardRouteRequestMapper, StandardRouteResponseMapper standardRouteResponseMapper, CurrentUserService currentUserService, MapboxAPIService mapboxAPIService) {
        this.standardRouteRepository = standardRouteRepository;
        this.routeStopRepository = routeStopRepository;
        this.userAccountRepository = userAccountRepository;
        this.administratorRepository = administratorRepository;
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
    public Page<StandardRouteResponseDTO> getAllStandardRouteByCustomer() {
        UUID customerId = TenantContext.getCurrentTenant();

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
                baseRoute.getCustomerId(),
                baseRoute.getStatus(),
                baseRoute.getCreatedAt(),
                baseRoute.getUpdatedAt()
        );
    }

    @Transactional
    public StandardRouteResponseDTO createStandardRoute(String authenticatedEmail, StandardRouteRequestDTO standardRouteRequestDTO) {
        UserAccount authenticatedUser = userAccountRepository.findUserByEmail(authenticatedEmail);

        if (authenticatedUser == null) throw new EntityNotFoundException("Usuário com o email " + authenticatedEmail + " não encontrado");

        // verifica se é um admin válido
        checkAdminPrivileges(authenticatedUser);
        checkValidAdmin(authenticatedUser);

        if (standardRouteRequestDTO.routeStops().isEmpty()) {
            throw new DomainValidationException("A rota padrão deve possuir ao menos um ponto de parada");
        }

        // obtém o customerID do contexto atual e valida existência
        UUID customerId = TenantContext.getCurrentTenant();
        if (customerId == null ) {
            throw new DomainValidationException("É necessário estar atuando sobre um Customer válido");
        }

        // valida duplicação do Nome no Customer
        if (standardRouteRepository.existsByRouteNameAndCustomerId(standardRouteRequestDTO.routeName(), customerId)) {
            throw new DuplicateResourceException("Já existe uma rota com o nome: " + standardRouteRequestDTO.routeName());
        }

        StandardRoute standardRoute = standardRouteRequestMapper.toEntity(standardRouteRequestDTO);

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

        List<RouteStop> routeStops = routeStopRepository.findAllById(routeStopIds);

        // validação de existência dos routestops
        if (routeStops.isEmpty()) {
            throw new EntityNotFoundException("Nenhum RouteStop encontrado");
        }

        if (routeStops.size() != standardRouteRequestDTO.routeStops().size()) {
            throw new EntityNotFoundException("Um ou mais RouteStops do DTO não foram encontrados");
        }

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

        // realiza a construção dos assignments
        List<RouteStopAssignment> assignments = standardRouteRequestDTO.routeStops().stream()
                .map(request -> {
                    RouteStop routeStop = routeStopsById.get(request.routeStopId());

                    if (routeStop == null) throw new EntityNotFoundException("RouteStop não encontrado");

                    validateSameCustomer(customerId, routeStop.getCustomerId());

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
        UserAccount authenticatedUser = userAccountRepository.findUserByEmail(authenticatedEmail);

        if (authenticatedUser == null) throw new EntityNotFoundException("Usuário com o email " + authenticatedEmail + " não encontrado");

        // verifica se é um ADMIN
        checkAdminPrivileges(authenticatedUser);
        checkValidAdmin(authenticatedUser);

        // obtém o customerID do contexto atual e valida existência
        UUID customerId = TenantContext.getCurrentTenant();
        if (customerId == null ) {
            throw new DomainValidationException("É necessário estar atuando sobre um Customer válido");
        }

        StandardRoute standardRoute = standardRouteRepository.findById(standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException("Rota padrão não encontrada: " + standardRouteId));

        // devem ser do mesmo Customer
        validateSameCustomer(standardRoute.getCustomerId(), customerId);

        // validação de duplicação de nome
        if (standardRouteUpdateDTO.routeName() != null && standardRouteUpdateDTO.routeName().equals(standardRoute.getRouteName())) {
            if (standardRouteRepository.existsByRouteNameAndCustomerIdAndIdNot(standardRouteUpdateDTO.routeName(), customerId, standardRouteId)){
                throw new IllegalArgumentException("Já existe uma rota com o nome: " + standardRouteUpdateDTO.routeName());
            }
        }

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

        standardRouteRequestMapper.standardRouteUpdateFromDTO(standardRouteUpdateDTO, standardRoute);

        // busca os assignments, em caso de null lança List.of para evitar NPE
        List<RouteStopAssignment> routeStopAssignments = standardRoute.getRouteStopAssignments() != null
                ? standardRoute.getRouteStopAssignments().stream()
                .sorted(Comparator.comparing(RouteStopAssignment::getSequence))
                .toList()
                : List.of();

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

        StandardRoute savedStandardRoute = standardRouteRepository.save(standardRoute);

        return standardRouteResponseMapper.toDTO(savedStandardRoute);
    }

    @Transactional
    public StandardRouteResponseDTO updateRouteStopPoints(UUID standardRouteId, String authenticatedEmail, StandardRouteStopsUpdateDTO standardRouteStopsUpdateDTO) {
        UserAccount authenticatedUser = userAccountRepository.findUserByEmail(authenticatedEmail);

        if (authenticatedUser == null) throw new EntityNotFoundException("Usuário com o email " + authenticatedEmail + " não encontrado");

        // verifica se é um ADMIN
        checkAdminPrivileges(authenticatedUser);
        checkValidAdmin(authenticatedUser);

        // obtém o customerID do contexto atual e valida existência
        UUID customerId = TenantContext.getCurrentTenant();
        if (customerId == null ) {
            throw new DomainValidationException("É necessário estar atuando sobre um Customer válido");
        }

        StandardRoute standardRoute = standardRouteRepository.findById(standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException("Entidade standardRoute não encontrada"));

        validateSameCustomer(customerId, standardRoute.getCustomerId());

        if (standardRouteStopsUpdateDTO == null || standardRouteStopsUpdateDTO.routeStops() == null || standardRouteStopsUpdateDTO.routeStops().isEmpty()) {
            throw new DomainValidationException("A rota padrão deve possuir ao menos um ponto de parada");
        }

        // pega os pontos de parada do DTO
        List<RouteStopAssignmentRequestDTO> requestedStops = standardRouteStopsUpdateDTO.routeStops().stream().toList();

        List<Integer> sequence = requestedStops.stream().map(RouteStopAssignmentRequestDTO::stopSequence).toList();

        // realiza a gama de validações com base na sequence extraída do DTO
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

        if (routeStops.size() != standardRouteStopsUpdateDTO.routeStops().size()) {
            throw new EntityNotFoundException("Um ou mais RouteStops não foram encontrados");
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

        // loop validando os customers
        for (RouteStop routeStop : routeStops) {
            validateSameCustomer(routeStop.getCustomerId(), customerId);
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

        // realiza limpeza e evita npe
        if (standardRoute.getRouteStopAssignments() == null) {
            standardRoute.setRouteStopAssignments(new ArrayList<>());
        } else {
            standardRoute.getRouteStopAssignments().clear();
        }

        // persiste através do cascade
        standardRoute.getRouteStopAssignments().addAll(assignments);

        StandardRoute savedStandardRoute = standardRouteRepository.save(standardRoute);

        return standardRouteResponseMapper.toDTO(savedStandardRoute);
    }

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

    private void checkAdminPrivileges(UserAccount authenticatedUser) {
        boolean isAdmin = authenticatedUser.getRoles().stream()
                .anyMatch(role -> role.equals("ROLE_ADMIN") || role.equals("ROLE_PLATFORM_ADMIN"));

        if (!isAdmin) {
            throw new NotAuthorizedException("Apenas Administradores e Administradores de Plataforma podem criar ou modificar rotas");
        }
    }

    private void checkValidAdmin(UserAccount authenticatedUser) {
        // realiza a validação p/ ver se o Filter do Spring Security conseguiu associar o Tenant (seja pelo JWT ou as act)
        if (TenantContext.getCurrentTenant() == null) {
            throw new DomainValidationException("O usuário autenticado não está associado a um Customer nesta requisição.");
        }

        Administrator admin = administratorRepository.findByEmail(authenticatedUser.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("Perfil de Administrador não encontrado para este usuário."));

        if (admin.getStatus() == GeneralStatus.INACTIVE) {
            throw new InactiveAccountModificationException("Usuário não está ativo.");
        }
    }

    private void validateSameCustomer(UUID firstCustomerId, UUID secondCustomerId) {
        if (firstCustomerId == null || secondCustomerId == null ||
                !firstCustomerId.equals(secondCustomerId)) {
            throw new CustomerMismatchException("Os recursos não pertencem ao mesmo Customer: " + "first: " + firstCustomerId + ", second: " + secondCustomerId);
        }
    }

}
