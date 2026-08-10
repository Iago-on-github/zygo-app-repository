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
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.RouteStopRepository;
import com.travel_system.backend_app.repository.StandardRouteRepository;
import com.travel_system.backend_app.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Valid;
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
        StandardRouteResponseDTO baseRoute = standardRouteRepository.findRouteBaseByIdAndStatus(standardRouteId, status)
                .orElseThrow(() -> new EntityNotFoundException("Rota não encontrada"));

        Set<RouteStopAssignmentResponseDTO> stops = standardRouteRepository.findAssignmentsByRouteId(standardRouteId);

        // mapeamento manual pois precisa fazer a inserão das rotas buscadas em uma query diferente
        return new StandardRouteResponseDTO(
                baseRoute.id(),
                baseRoute.routeName(),
                baseRoute.routeDescription(),
                baseRoute.originLatitude(),
                baseRoute.originLongitude(),
                baseRoute.destinationLatitude(),
                baseRoute.destinationLongitude(),
                baseRoute.standardGeometry(),
                baseRoute.travelPeriod(),
                stops, // routeStops
                baseRoute.customerId(),
                baseRoute.status(),
                baseRoute.createdAt(),
                baseRoute.updatedAt()
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

        if (isDuplicatedName) throw new IllegalArgumentException("Já existe uma rota com o nome: " + standardRouteRequestDTO.routeName());

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

        // importante: caso o cascade do relacionamento seja removido é necessário salvar o RouteStopAssignments explicitamente
        StandardRoute savedStandardRoute = standardRouteRepository.save(standardRoute);

        standardRoute.setCreatedAt(Instant.now());
        standardRoute.setStatus(GeneralStatus.ACTIVE);

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

        Double originLat = standardRouteUpdateDTO.originLatitude() != null ? standardRouteUpdateDTO.originLatitude() : standardRoute.getOriginLatitude();
        Double originLng = standardRouteUpdateDTO.originLongitude() != null ? standardRouteUpdateDTO.originLongitude() : standardRoute.getOriginLongitude();

        Double destinationLat = standardRouteUpdateDTO.destinationLatitude() != null ? standardRouteUpdateDTO.destinationLatitude() : standardRoute.getDestinationLatitude();
        Double destinationLng = standardRouteUpdateDTO.destinationLongitude() != null ? standardRouteUpdateDTO.destinationLongitude() : standardRoute.getDestinationLongitude();

        if ((originLat == null) != (originLng == null)) {
            throw new NoSuchCoordinates("Latitude e longitude de origem devem ser informadas juntas");
        }

        if ((destinationLat == null) != (destinationLng == null)) {
            throw new NoSuchCoordinates("Latitude e longitude do destino devem ser informadas juntas");
        }

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

        return standardRouteResponseMapper.toDTO(savedStandardRoute);
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

    @Transactional
    public void associateRouteStopWithStandardRoute(UUID standardRouteId, UUID routeStopId, int sequence, boolean isOptionalSpot) {
        if (sequence <= 0) throw new IllegalArgumentException("A ordem de sequencia da rota deve ser maior que zero: " + sequence);

        StandardRoute standardRoute = standardRouteRepository.findById(standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException("Rota padrão não encontrada"));

        RouteStop routeStop = routeStopRepository.findById(routeStopId).orElseThrow(() -> new EntityNotFoundException("Ponto de parada não encontrado"));

        validateSameCustomer(standardRoute.getCustomer().getId(), routeStop.getCustomer().getId());

        if (standardRoute.getStatus().equals(GeneralStatus.INACTIVE) || routeStop.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new IllegalArgumentException("A Rota padrão ou o poto de parada está inativo");
        }

        standardRoute.getRouteStopAssignments().forEach(assignments -> {
            Integer existsSequence = assignments.getSequence();

            if (routeStopId.equals(assignments.getRouteStop().getId())) {
                throw new IllegalArgumentException("Esse Ponto de Parada já está vinculada à rota: " + standardRouteId);
            }

            if (existsSequence == sequence) {
                throw new IllegalArgumentException("Já existe um Ponto de Parada nessa ordem de sequência: " + sequence);
            }
        });

        RouteStopAssignment routeStopAssignment = new RouteStopAssignment();

        standardRoute.getRouteStopAssignments().add(routeStopAssignment); // adiciona à list sem remover os já existentes

        routeStopAssignment.setStandardRoute(standardRoute);
        routeStopAssignment.setRouteStop(routeStop);
        routeStopAssignment.setSequence(sequence);
        routeStopAssignment.setOptionalSpot(isOptionalSpot);

        List<RouteStopAssignment> assignmentsOrderedBySequence = standardRoute.getRouteStopAssignments().stream()
                .sorted(Comparator.comparing(RouteStopAssignment::getSequence)).toList();

        // constroi os waypoints
        List<Point> waypoints = buildWaypoints(assignmentsOrderedBySequence);

        // recalcula geometry
        RouteDetailsDTO routeDetailsDTO = calculateStandardRouteGeometry(standardRoute.getOriginLongitude(),
                standardRoute.getOriginLatitude(),
                standardRoute.getDestinationLongitude(),
                standardRoute.getDestinationLatitude(),
                waypoints);

        standardRoute.setStandardGeometry(routeDetailsDTO.geometry());

        standardRouteRepository.save(standardRoute);
    }

    @Transactional
    public void removeRouteStopWithStandardRoute(UUID standardRouteId, UUID routeStopId) {
        StandardRoute standardRoute = standardRouteRepository.findById(standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException("Rota padrão não encontrada"));

        RouteStop routeStop = routeStopRepository.findById(routeStopId).orElseThrow(() -> new EntityNotFoundException("Ponto de parada não encontrado"));

        validateSameCustomer(standardRoute.getCustomer().getId(), routeStop.getCustomer().getId());

        if (standardRoute.getStatus().equals(GeneralStatus.INACTIVE) || routeStop.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new IllegalArgumentException("A Rota padrão ou o poto de parada está inativo");
        }

        // realiza a remoção de forma segura
        standardRoute.getRouteStopAssignments().removeIf(assignment -> assignment.getRouteStop().getId().equals(routeStopId));

        List<RouteStopAssignment> remainingAssignments = standardRoute.getRouteStopAssignments().stream()
                .sorted(Comparator.comparing(RouteStopAssignment::getSequence)).toList();

        // caso não haja mais nenhuma rota simplesmente faz a alteração e salva sem recalcular
        if (remainingAssignments.isEmpty()) {
            standardRouteRepository.save(standardRoute);
            return;
        }

        // realiza a reorganização dos indicies restantes, mantendo ordem (1, 2, 3...)
        int sequence = 1;
        for (RouteStopAssignment assignment : remainingAssignments) {
            assignment.setSequence(sequence++);
        }

        // recupera os assignments restantes ordenados pela sequencia
        List<RouteStopAssignment> assignmentsOrderedBySequence = remainingAssignments.stream()
                .sorted(Comparator.comparing(RouteStopAssignment::getSequence)).toList();

        // constroi os waypoints
        List<Point> waypoints = buildWaypoints(assignmentsOrderedBySequence);

        // recalcula e valida geometry
        RouteDetailsDTO routeDetailsDTO = calculateStandardRouteGeometry(
                standardRoute.getOriginLongitude(),
                standardRoute.getOriginLatitude(),
                standardRoute.getDestinationLongitude(),
                standardRoute.getDestinationLatitude(),
                waypoints);

        standardRoute.setStandardGeometry(routeDetailsDTO.geometry());

        standardRouteRepository.save(standardRoute);
    }

    @Transactional
    public StandardRouteResponseDTO reorderRouteStops(UUID standardRouteId, List<RouteStopReorderRequestDTO> routeStopsReorder) {
        if (standardRouteId == null) {
            throw new IllegalArgumentException("standardRouteId não pode ser nulo");
        }

        if (routeStopsReorder == null || routeStopsReorder.isEmpty()) {
            throw new DomainValidationException(
                    "É necessário informar os pontos de parada para reordenar"
            );
        }

        StandardRoute standardRoute = standardRouteRepository.findById(standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException(
                                "Rota padrão não encontrada: " + standardRouteId));

        if (standardRoute.getStatus() == GeneralStatus.INACTIVE) {
            throw new DomainValidationException(
                    "Não é possível reorganizar os pontos de uma rota padrão inativa"
            );
        }

        List<RouteStopAssignment> currentAssignments = getRouteStopAssignments(routeStopsReorder, standardRoute);

        Set<UUID> requestedRouteStopIds = new HashSet<>();
        Set<Integer> requestedSequences = new HashSet<>();

        // garante que o processamento não continue com dados inválidos ou insuficientes
        for (RouteStopReorderRequestDTO request : routeStopsReorder) {

            if (request == null) {
                throw new DomainValidationException("A requisição de reorganização não pode possuir itens nulos");
            }

            if (request.routeStopId() == null) {
                throw new DomainValidationException("RouteStopId não pode ser nulo");
            }

            if (request.newSequence() <= 0) {
                throw new DomainValidationException("A nova sequência deve ser maior que zero");
            }

            if (!requestedRouteStopIds.add(request.routeStopId())) {
                throw new DomainValidationException("RouteStopId duplicado: " + request.routeStopId());
            }

            if (!requestedSequences.add(request.newSequence())) {
                throw new DomainValidationException("newSequence duplicada: " + request.newSequence());
            }
        }

        /* Valida a sequência contíunua
         * Se existem 3 pontos:  1, 2, 3
         * e não: 1, 3, 7
         */
        int expectedSequence = 1;

        List<Integer> orderedRequestedSequences = requestedSequences.stream()
                .sorted()
                .toList();

        for (Integer sequence : orderedRequestedSequences) {
            if (sequence != expectedSequence) {
                throw new DomainValidationException("As novas sequências devem ser consecutivas começando em 1");
            }

            expectedSequence++;
        }

        // mapa com os assignments atuais
        Map<UUID, RouteStopAssignment> assignmentsByRouteStopId =
                currentAssignments.stream()
                        .collect(Collectors.toMap(
                                assignment -> assignment.getRouteStop().getId(),
                                Function.identity()
                        ));

        // garante que os routeStops pertençam a mesma rota
        for (UUID requestedRouteStopId : requestedRouteStopIds) {
            if (!assignmentsByRouteStopId.containsKey(requestedRouteStopId)) {
                throw new EntityNotFoundException("O RouteStop " + requestedRouteStopId + " não está associado à rota " + standardRouteId);
            }
        }

        List<RouteStop> routeStops = routeStopRepository.findAllById(requestedRouteStopIds);

        if (routeStops.size() != requestedRouteStopIds.size()) {
            throw new EntityNotFoundException("Um ou mais RouteStops informados não foram encontrados");
        }

        UUID customerId = standardRoute.getCustomer().getId();

        // valida customer e coords válidas do RouteStop
        for (RouteStop routeStop : routeStops) {
            validateSameCustomer(customerId, routeStop.getCustomer().getId());

            if (routeStop.getStatus() == GeneralStatus.INACTIVE) {
                throw new DomainValidationException("O RouteStop " + routeStop.getId() + " está inativo e não pode participar da rota");
            }

            if (routeStop.getLatitude() == null || routeStop.getLongitude() == null) {
                throw new DomainValidationException("O RouteStop " + routeStop.getId() + " não possui coordenadas válidas");
            }
        }

        // map para estabelecer o novo relacionamento entre o routeStop (id) e a nova sequence
        Map<UUID, Integer> newSequenceByRouteStopId =
                routeStopsReorder.stream()
                        .collect(Collectors.toMap(
                                RouteStopReorderRequestDTO::routeStopId,
                                RouteStopReorderRequestDTO::newSequence
                        ));

        // atualiza os sequences para evitar problemas com constraints uniques no banco de dados
        // atualmente não faz muita coisa, ele é um artefato defensivo, jaq atualmente essa constraint não existe mas caso seja válido adicionar no futuro o código já está adaptado
        int temporarySequence = -1;
        for (RouteStopAssignment assignment : currentAssignments) {
            assignment.setSequence(temporarySequence--);
        }


        // aplicação definitiva das sequences
        for (RouteStopAssignment assignment : currentAssignments) {
            UUID routeStopId = assignment.getRouteStop().getId();
            Integer newSequence = newSequenceByRouteStopId.get(routeStopId);

            if (newSequence == null) {
                throw new DomainValidationException("Não foi possível determinar a nova sequência para o RouteStop " + routeStopId);
            }

            assignment.setSequence(newSequence);
        }

        // com base na nova ordem agora já armazenada em memória dos assignments, ordenamos novamente pelo sequence e fazemos
        // o processamento de recálculo padrão
        List<RouteStopAssignment> assignmentsOrderedBySequence =
                currentAssignments.stream()
                        .sorted(Comparator.comparing(RouteStopAssignment::getSequence))
                        .toList();


        List<Point> waypoints = buildWaypoints(assignmentsOrderedBySequence);

        RouteDetailsDTO routeDetailsDTO = calculateStandardRouteGeometry(
                        standardRoute.getOriginLongitude(),
                        standardRoute.getOriginLatitude(),
                        standardRoute.getDestinationLongitude(),
                        standardRoute.getDestinationLatitude(),
                        waypoints);

        standardRoute.setStandardGeometry(routeDetailsDTO.geometry());

        StandardRoute savedStandardRoute = standardRouteRepository.save(standardRoute);

        return standardRouteResponseMapper.toDTO(savedStandardRoute
        );
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

    private static List<RouteStopAssignment> getRouteStopAssignments(List<RouteStopReorderRequestDTO> routeStopsReorder, StandardRoute standardRoute) {
        List<RouteStopAssignment> currentAssignments = standardRoute.getRouteStopAssignments();

        if (currentAssignments == null || currentAssignments.isEmpty()) {
            throw new DomainValidationException(
                    "A rota padrão não possui pontos de parada associados"
            );
        }

        if (routeStopsReorder.size() != currentAssignments.size()) {
            throw new DomainValidationException(
                    "A quantidade de RouteStops informada deve ser igual à quantidade " +
                            "de RouteStops atualmente associada à rota"
            );
        }
        return currentAssignments;
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
