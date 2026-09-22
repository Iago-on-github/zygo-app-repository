package com.travel_system.backend_app.service;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.StandardRouteRequestMapper;
import com.travel_system.backend_app.interfaces.mappers.response.StandardRouteResponseMapper;
import com.travel_system.backend_app.model.RouteStop;
import com.travel_system.backend_app.model.RouteStopAssignment;
import com.travel_system.backend_app.model.StandardRoute;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.dtos.request.RouteStopReorderRequestDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.dtos.route.AssociateRouteStopToStandardRouteDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelDirection;
import com.travel_system.backend_app.repository.RouteStopRepository;
import com.travel_system.backend_app.repository.StandardRouteRepository;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RouteStopAssignmentService {

    private final StandardRouteRepository standardRouteRepository;
    private final RouteStopRepository routeStopRepository;

    private final StandardRouteResponseMapper standardRouteResponseMapper;

    private final MapboxAPIService mapboxAPIService;

    private final EntityManager entityManager;

    public RouteStopAssignmentService(StandardRouteRepository standardRouteRepository, RouteStopRepository routeStopRepository, UserAccountRepository userAccountRepository, StandardRouteRequestMapper standardRouteRequestMapper, StandardRouteResponseMapper standardRouteResponseMapper, CurrentUserService currentUserService, MapboxAPIService mapboxAPIService, EntityManager entityManager) {
        this.standardRouteRepository = standardRouteRepository;
        this.routeStopRepository = routeStopRepository;
        this.standardRouteResponseMapper = standardRouteResponseMapper;
        this.mapboxAPIService = mapboxAPIService;
        this.entityManager = entityManager;
    }

    @Transactional
    public void associateRouteStopWithStandardRoute(UUID standardRouteId, UUID routeStopId, AssociateRouteStopToStandardRouteDTO dto) {
        int sequence = dto.sequence();
        boolean optionalSpot = dto.isOptionalSpot();
        TravelDirection travelDirection = dto.travelDirection();

        if (sequence <= 0) throw new IllegalArgumentException("A ordem de sequencia da rota deve ser maior que zero: " + sequence);

        StandardRoute standardRoute = standardRouteRepository.findById(standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException("Rota padrão não encontrada"));

        RouteStop routeStop = routeStopRepository.findById(routeStopId).orElseThrow(() -> new EntityNotFoundException("Ponto de parada não encontrado"));

        validateSameCustomer(standardRoute.getCustomerId(), routeStop.getCustomerId());

        if (standardRoute.getStatus().equals(GeneralStatus.INACTIVE) || routeStop.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new IllegalArgumentException("A Rota padrão ou o poto de parada está inativo");
        }

        standardRoute.getRouteStopAssignments().forEach(assignments -> {
            Integer existsSequence = assignments.getSequence();
            TravelDirection existsDirection = assignments.getTravelDirection();

            if (routeStopId.equals(assignments.getRouteStop().getId()) && existsDirection == travelDirection) {
                throw new IllegalArgumentException("Esse Ponto de Parada já está vinculada à rota: " + standardRouteId + " para a direção: " + travelDirection);
            }

            if (existsSequence == sequence && existsDirection == travelDirection) {
                throw new IllegalArgumentException("Já existe um Ponto de Parada nessa ordem de sequência: " + sequence + " para o direção: " + travelDirection);
            }
        });

        RouteStopAssignment routeStopAssignment = new RouteStopAssignment();

        standardRoute.getRouteStopAssignments().add(routeStopAssignment); // adiciona à list sem remover os já existentes

        routeStopAssignment.setStandardRoute(standardRoute);
        routeStopAssignment.setRouteStop(routeStop);
        routeStopAssignment.setSequence(sequence);
        routeStopAssignment.setOptionalSpot(optionalSpot);
        routeStopAssignment.setTravelDirection(travelDirection);

        List<RouteStopAssignment> assignmentsOrderedBySequence = standardRoute.getRouteStopAssignments().stream()
                .filter(direction -> direction.getTravelDirection().equals(travelDirection))
                .sorted(Comparator.comparing(RouteStopAssignment::getSequence)).toList();

        // constroi os waypoints
        List<Point> waypoints = buildWaypoints(assignmentsOrderedBySequence);

        /*
        * calcula geometry com base na direction informada da rota padrão
        * para ida origin vai primeiro, para volta, destination vai primeiro
        * */
        if (travelDirection == TravelDirection.OUTBOUND) {
            RouteDetailsDTO routeDetailsDTO = calculateStandardRouteGeometry(
                    standardRoute.getOriginLongitude(),
                    standardRoute.getOriginLatitude(),
                    standardRoute.getDestinationLongitude(),
                    standardRoute.getDestinationLatitude(),
                    waypoints);

            standardRoute.setStandardGeometryOutbound(routeDetailsDTO.geometry());

        } else {
            RouteDetailsDTO routeDetailsDTO = calculateStandardRouteGeometry(
                    standardRoute.getDestinationLongitude(),
                    standardRoute.getDestinationLatitude(),
                    standardRoute.getOriginLongitude(),
                    standardRoute.getOriginLatitude(),
                    waypoints);

            standardRoute.setStandardGeometryReturn(routeDetailsDTO.geometry());
        }

        standardRouteRepository.save(standardRoute);
    }

    @Transactional
    public void removeRouteStopWithStandardRoute(UUID standardRouteId, UUID routeStopId, TravelDirection travelDirection) {
        if (travelDirection == null) {
            throw new InvalidTravelDirectionException("Travel Direction null. Não é possível prosseguir. StandardRouteId: " + standardRouteId);
        }

        StandardRoute standardRoute = standardRouteRepository.findById(standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException("Rota padrão não encontrada"));

        RouteStop routeStop = routeStopRepository.findById(routeStopId)
                .orElseThrow(() -> new EntityNotFoundException("Ponto de parada não encontrado"));

        validateSameCustomer(standardRoute.getCustomerId(), routeStop.getCustomerId());

        // verifica se o routestop faz parte da standardRoute com base no direction
        boolean isAssignment = standardRoute.getRouteStopAssignments().stream()
                .filter(direction -> direction.getTravelDirection() == travelDirection)
                .anyMatch(id -> id.getRouteStop().getId().equals(routeStopId));

        if (!isAssignment) {
            throw new DomainValidationException("Ponto de parada " +  routeStop.getName() + " não existe na rota padrão: " + standardRoute.getRouteName() + "para a direção: " + travelDirection);
        }

/*        if (standardRoute.getStatus().equals(GeneralStatus.INACTIVE) || routeStop.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new IllegalArgumentException("A Rota padrão ou o poto de parada está inativo");
        }*/

        // realiza a remoção de forma segura com base no direction
        standardRoute.getRouteStopAssignments().removeIf(assignment -> assignment.getRouteStop().getId().equals(routeStopId)
                && assignment.getTravelDirection() == travelDirection);

        List<RouteStopAssignment> remainingAssignments = standardRoute.getRouteStopAssignments().stream()
                .filter(direction -> direction.getTravelDirection() == travelDirection)
                .sorted(Comparator.comparing(RouteStopAssignment::getSequence)).toList();

        // caso não haja mais nenhuma rota simplesmente faz a alteração e salva sem recalcular
        if (remainingAssignments.isEmpty()) {
            standardRouteRepository.save(standardRoute);
            return;
        }

        // realiza a reorganização dos indicies restantes, mantendo ordem (1, 2, 3...) e obtém a informação da direção
        int sequence = 1;
        for (RouteStopAssignment assignment : remainingAssignments) {
            assignment.setSequence(sequence++);
        }

        // recupera os assignments restantes ordenados pela sequencia
        List<RouteStopAssignment> assignmentsOrderedBySequence = remainingAssignments.stream()
                .sorted(Comparator.comparing(RouteStopAssignment::getSequence)).toList();

        // constroi os waypoints
        List<Point> waypoints = buildWaypoints(assignmentsOrderedBySequence);

        /*
         * calcula geometry com base na direction informada da rota padrão (após a remoção já ter ocorrido)
         * para ida origin vai primeiro, para volta, destination vai primeiro
         * */
        if (travelDirection == TravelDirection.OUTBOUND) {

            RouteDetailsDTO routeDetailsDTO = calculateStandardRouteGeometry(
                    standardRoute.getOriginLongitude(),
                    standardRoute.getOriginLatitude(),
                    standardRoute.getDestinationLongitude(),
                    standardRoute.getDestinationLatitude(),
                    waypoints);

            standardRoute.setStandardGeometryOutbound(routeDetailsDTO.geometry());

        } else {

            RouteDetailsDTO routeDetailsDTO = calculateStandardRouteGeometry(
                    standardRoute.getDestinationLongitude(),
                    standardRoute.getDestinationLatitude(),
                    standardRoute.getOriginLongitude(),
                    standardRoute.getOriginLatitude(),
                    waypoints);

            standardRoute.setStandardGeometryReturn(routeDetailsDTO.geometry());
        }

        standardRouteRepository.save(standardRoute);
    }

    @Transactional
    public StandardRouteResponseDTO reorderRouteStops(UUID standardRouteId, List<RouteStopReorderRequestDTO> routeStopsReorder) {
        StandardRoute standardRoute = standardRouteRepository.findById(standardRouteId)
                .orElseThrow(() -> new EntityNotFoundException("Rota padrão não encontrada: " + standardRouteId));

        if (standardRoute.getStatus() == GeneralStatus.INACTIVE) {
            throw new DomainValidationException("Não é possível reorganizar os pontos de uma rota padrão inativa");
        }

        // direção única desta chamada, extraída do primeiro item da requisição
        TravelDirection travelDirection = routeStopsReorder.getFirst().travelDirection();

        // garante que todos os itens pertençam à mesma direção (opera em uma direção por vez)
        boolean allSameDirection = routeStopsReorder.stream()
                .allMatch(item -> item.travelDirection() == travelDirection);

        if (!allSameDirection) {
            throw new InvalidTravelDirectionException("Todos os pontos informados devem pertencer à mesma direção.");
        }

        // recupera os assignments já filtrados pela direção informada
        List<RouteStopAssignment> currentAssignments = getRouteStopAssignments(routeStopsReorder.getFirst(), standardRoute);

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

        /* Valida a sequência contínua
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

        // quantidade informada deve bater com os assignments atuais da rota para esta direção
        if (routeStopsReorder.size() != currentAssignments.size()) {
            throw new DomainValidationException(
                    "A quantidade de RouteStops informada deve ser igual à quantidade " +
                            "de RouteStops atualmente associada à rota para a direção: " + travelDirection
            );
        }

        // mapa com os assignments atuais (já escopados pela direção)
        Map<UUID, RouteStopAssignment> assignmentsByRouteStopId =
                currentAssignments.stream()
                        .collect(Collectors.toMap(
                                assignment -> assignment.getRouteStop().getId(),
                                Function.identity()
                        ));

        // garante que os routeStops pertençam à mesma rota, na direção informada
        for (UUID requestedRouteStopId : requestedRouteStopIds) {
            if (!assignmentsByRouteStopId.containsKey(requestedRouteStopId)) {
                throw new EntityNotFoundException("O RouteStop " + requestedRouteStopId + " não está associado à rota " + standardRouteId + " para a direção: " + travelDirection);
            }
        }

        List<RouteStop> routeStops = routeStopRepository.findAllById(requestedRouteStopIds);

        if (routeStops.size() != requestedRouteStopIds.size()) {
            throw new EntityNotFoundException("Um ou mais RouteStops informados não foram encontrados");
        }

        UUID customerId = standardRoute.getCustomerId();

        // valida customer e coords válidas do RouteStop
        for (RouteStop routeStop : routeStops) {
            validateSameCustomer(customerId, routeStop.getCustomerId());

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
        int temporarySequence = -1;
        for (RouteStopAssignment assignment : currentAssignments) {
            assignment.setSequence(temporarySequence--);
        }

        // envia os updates com os valores negativos, liberando os valores positivos para uso
        entityManager.flush();

        // aplicação definitiva das sequences
        for (RouteStopAssignment assignment : currentAssignments) {
            UUID routeStopId = assignment.getRouteStop().getId();
            Integer newSequence = newSequenceByRouteStopId.get(routeStopId);

            if (newSequence == null) {
                throw new DomainValidationException("Não foi possível determinar a nova sequência para o RouteStop " + routeStopId);
            }

            assignment.setSequence(newSequence);
        }

        // com base na nova ordem agora já armazenada em memória dos assignments, ordena novamente pelo sequence e faz
        // o processamento de recálculo padrão
        List<RouteStopAssignment> assignmentsOrderedBySequence = currentAssignments.stream()
                .sorted(Comparator.comparing(RouteStopAssignment::getSequence))
                .toList();

        // constroi os waypoints
        List<Point> waypoints = buildWaypoints(assignmentsOrderedBySequence);

        /*
         * calcula geometry com base na direção única desta chamada
         * para ida origin vai primeiro, para volta, destination vai primeiro
         */
        if (travelDirection == TravelDirection.OUTBOUND) {

            RouteDetailsDTO routeDetailsDTO = calculateStandardRouteGeometry(
                    standardRoute.getOriginLongitude(),
                    standardRoute.getOriginLatitude(),
                    standardRoute.getDestinationLongitude(),
                    standardRoute.getDestinationLatitude(),
                    waypoints);

            standardRoute.setStandardGeometryOutbound(routeDetailsDTO.geometry());

        } else {

            RouteDetailsDTO routeDetailsDTO = calculateStandardRouteGeometry(
                    standardRoute.getDestinationLongitude(),
                    standardRoute.getDestinationLatitude(),
                    standardRoute.getOriginLongitude(),
                    standardRoute.getOriginLatitude(),
                    waypoints);

            standardRoute.setStandardGeometryReturn(routeDetailsDTO.geometry());
        }

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

    private static List<RouteStopAssignment> getRouteStopAssignments(RouteStopReorderRequestDTO routeStopsReorder, StandardRoute standardRoute) {
        List<RouteStopAssignment> currentAssignments = standardRoute.getRouteStopAssignments()
                .stream().filter(direction -> direction.getTravelDirection() == routeStopsReorder.travelDirection()).toList();

        if (currentAssignments.isEmpty()) {
            throw new DomainValidationException("A rota padrão não possui pontos de parada associados para a direção: " + routeStopsReorder.travelDirection());
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
