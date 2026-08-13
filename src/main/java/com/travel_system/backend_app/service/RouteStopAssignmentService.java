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
import com.travel_system.backend_app.model.dtos.request.RouteStopReorderRequestDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.repository.RouteStopRepository;
import com.travel_system.backend_app.repository.StandardRouteRepository;
import com.travel_system.backend_app.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RouteStopAssignmentService {

    private final StandardRouteRepository standardRouteRepository;
    private final RouteStopRepository routeStopRepository;

    private final StandardRouteResponseMapper standardRouteResponseMapper;

    private final MapboxAPIService mapboxAPIService;

    public RouteStopAssignmentService(StandardRouteRepository standardRouteRepository, RouteStopRepository routeStopRepository, UserRepository userRepository, StandardRouteRequestMapper standardRouteRequestMapper, StandardRouteResponseMapper standardRouteResponseMapper, CurrentUserService currentUserService, MapboxAPIService mapboxAPIService) {
        this.standardRouteRepository = standardRouteRepository;
        this.routeStopRepository = routeStopRepository;
        this.standardRouteResponseMapper = standardRouteResponseMapper;
        this.mapboxAPIService = mapboxAPIService;
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


    private void validateSameCustomer(UUID firstCustomerId, UUID secondCustomerId) {
        if (firstCustomerId == null || secondCustomerId == null ||
                !firstCustomerId.equals(secondCustomerId)) {
            throw new CustomerMismatchException("Os recursos não pertencem ao mesmo Customer: " + "first: " + firstCustomerId + ", second: " + secondCustomerId);
        }
    }
}
