package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.InitializeStudentTravelRouteStopEvent;
import com.travel_system.backend_app.events.InvalidStudentTravelRouteStopEvent;
import com.travel_system.backend_app.exceptions.InactiveAccountException;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.enums.StudentTravelRouteStopStatus;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.repository.StudentTravelRouteStopRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*
* os eventos de mudança de estado e notificações são publicados de forma individual nos respectivos listeners
* roda de forma async no sistema principal de tracking
* os eventos publicados devem ser específicos sobre cada estado, e não de forma genérica
* os eventos que fizerem operações pesadas devem rodar de forma async
*
* */

@Service
public class StudentTravelRouteStopService {
    private final StudentTravelRepository studentTravelRepository;
    private final StudentTravelRouteStopRepository studentTravelRouteStopRepository;

    private final LocationService locationService;
    private final TravelTrackingNotificationService trackingNotificationService;

    private final ApplicationEventPublisher eventPublisher;

    private static final Double MONITORING_THRESHOLD = 4.0;
    private static final Double APPROACHING_THRESHOLD = 1.5;
    private static final Double REACHED_THRESHOLD = 50.0;

    public StudentTravelRouteStopService(StudentTravelRepository studentTravelRepository, StudentTravelRouteStopRepository studentTravelRouteStopRepository, LocationService locationService, TravelTrackingNotificationService trackingNotificationService, ApplicationEventPublisher eventPublisher) {
        this.studentTravelRepository = studentTravelRepository;
        this.studentTravelRouteStopRepository = studentTravelRouteStopRepository;
        this.locationService = locationService;
        this.trackingNotificationService = trackingNotificationService;
        this.eventPublisher = eventPublisher;
    }

    // valida se StudentTravel está associado a um RouteStop compatível com a Rota Padrão da viagem
    public void validateStudentTravelRouteStop(UUID studentTravelId) {
        StudentTravel studentTravel = studentTravelRepository.findById(studentTravelId)
                .orElseThrow(() -> new EntityNotFoundException("Entidade não encontrada: " + studentTravelId));

        if (!studentTravel.getStudentTravelStatus().equals(StudentTravelStatus.ACTIVE)) {
            throw new InactiveAccountException("StudentTravel não está ativo na viagem");
        }

        UUID travelId = studentTravel.getTravel().getId();
        UUID studentId = studentTravel.getStudent().getId();
        UUID customerId = studentTravel.getStudent().getCustomer().getId();

        // rota padrão da viagem na qual o estudante está vínculado
        StandardRoute standardRoute = studentTravel.getTravel().getStandardRoute();

        // ids dos pontos de paradas vinculados ao estudante
        List<UUID> studentRouteStopIds = studentTravel.getStudentTravelRouteStops().stream()
                .map(routeStopsId -> routeStopsId.getRouteStop().getId()).toList();

        // verifica se existe algum ponto de parada do estudante vinculado na rota padrão
        boolean routeStopsAreCorresponding = standardRoute.getRouteStopAssignments().stream()
                .anyMatch(id -> studentRouteStopIds.contains(id.getRouteStop().getId()));

        // se não houver, publica evento no listener responsável pela incompatibilidade
        if (!routeStopsAreCorresponding) {
            Instant lastValidatedAt = Instant.now();

            InvalidStudentTravelRouteStopEvent routeStopEvent = new InvalidStudentTravelRouteStopEvent(studentTravelId, studentId, travelId, customerId, StudentTravelRouteStopStatus.INVALID_ROUTE, lastValidatedAt);

            eventPublisher.publishEvent(routeStopEvent);
        }
    }

    // inicia o acompanhamento do ponto do estudante
    public void initializeStudentTravelRouteStopTracking(UUID studentTravelId) {
        StudentTravel studentTravel = studentTravelRepository.findById(studentTravelId)
                .orElseThrow(() -> new EntityNotFoundException("Entidade não encontrada: " + studentTravelId));

        if (!studentTravel.getStudentTravelStatus().equals(StudentTravelStatus.ACTIVE)) {
            throw new InactiveAccountException("StudentTravel não está ativo na viagem");
        }

        UUID travelId = studentTravel.getTravel().getId();
        UUID studentId = studentTravel.getStudent().getId();
        UUID customerId = studentTravel.getStudent().getCustomer().getId();

        // período da viagem atual
        TravelPeriod travelPeriod = studentTravel.getTravel().getTravelPeriod();

        // pega os routeStops do estudante
        List<RouteStop> studentRouteStops = studentTravel.getStudentTravelRouteStops().stream().map(StudentTravelRouteStop::getRouteStop).toList();

        for (RouteStop studentStop : studentRouteStops) {

            // procura o ponto de parada correspondente do aluno com base no turno da viagem e do ponto
            Optional<StudentRouteStopAssignment> matchingAssignment = studentStop.getStudentRouteStopAssignments().stream()
                    .filter(assignment -> assignment.getTravelPeriod().equals(travelPeriod))
                    .findFirst();

            if (matchingAssignment.isPresent()) {
                RouteStop routeStopToTrack = studentStop;

                UUID routeStopId = routeStopToTrack.getId();
                Double routeStopIdLatitude = routeStopToTrack.getLatitude();
                Double routeStopIdLongitude = routeStopToTrack.getLongitude();
                StudentTravelRouteStopStatus studentTravelRouteStopStatus = StudentTravelRouteStopStatus.EXPECTED;

                InitializeStudentTravelRouteStopEvent event = new InitializeStudentTravelRouteStopEvent(
                        studentId,
                        travelId,
                        routeStopId,
                        customerId,
                        routeStopIdLatitude,
                        routeStopIdLongitude,
                        MONITORING_THRESHOLD,
                        studentTravelRouteStopStatus
                );

                eventPublisher.publishEvent(event);
            }
        }
    }
}
