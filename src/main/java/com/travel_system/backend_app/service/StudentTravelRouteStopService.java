package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.*;
import com.travel_system.backend_app.model.dtos.cache.StudentTravelRouteStopTrackingCacheDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.response.DistanceResponseDTO;
import com.travel_system.backend_app.model.enums.StudentTravelRouteStopStatus;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.repository.StudentTravelRouteStopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static com.travel_system.backend_app.config.constants.GlobalAppConstants.*;

/*
* os eventos de mudança de estado e notificações são publicados de forma individual nos respectivos listeners
* roda de forma async no sistema principal de tracking
* os eventos publicados devem ser específicos sobre cada estado, e não de forma genérica
* os eventos que fizerem operações pesadas devem rodar de forma async
*
* */

@Service
public class StudentTravelRouteStopService {
    private final Logger log = LoggerFactory.getLogger(StudentTravelRouteStopService.class);

    private final StudentTravelRepository studentTravelRepository;
    private final StudentTravelRouteStopRepository studentTravelRouteStopRepository;

    private final LocationService locationService;
    private final RedisTrackingService redisTrackingService;
    private final TravelTrackingNotificationService trackingNotificationService;
    private final TravelTrackingStaticCache travelTrackingStaticCache;

    private final ApplicationEventPublisher eventPublisher;

    public StudentTravelRouteStopService(StudentTravelRepository studentTravelRepository, StudentTravelRouteStopRepository studentTravelRouteStopRepository, LocationService locationService, RedisTrackingService redisTrackingService, TravelTrackingNotificationService trackingNotificationService, TravelTrackingStaticCache travelTrackingStaticCache, ApplicationEventPublisher eventPublisher) {
        this.studentTravelRepository = studentTravelRepository;
        this.studentTravelRouteStopRepository = studentTravelRouteStopRepository;
        this.locationService = locationService;
        this.redisTrackingService = redisTrackingService;
        this.trackingNotificationService = trackingNotificationService;
        this.travelTrackingStaticCache = travelTrackingStaticCache;
        this.eventPublisher = eventPublisher;
    }

    // publica evento quando o estudante não está vinculado a nenhum ponto de parda da rota padrão
    public void validateStudentTravelRouteStop(UUID travelId, UUID studentTravelId, UUID studentId, UUID customerId) {
        Instant lastValidatedAt = Instant.now();

        InvalidStudentTravelRouteStopEvent routeStopEvent = new InvalidStudentTravelRouteStopEvent(studentTravelId, studentId, travelId, customerId, StudentTravelRouteStopStatus.INVALID_ROUTE, lastValidatedAt);

        eventPublisher.publishEvent(routeStopEvent);
    }

    /*
    * inicia o acompanhamento do estudante, encontra o ponto de parada compatível com o período da viagem e publica evento
    * */
    public void initializeStudentTravelRouteStopTracking(UUID travelId, UUID studentTravelId) {
        // recupera os dados de monitoriamento
        StudentTravelRouteStopTrackingCacheDTO trackingData = travelTrackingStaticCache.getStudentTravelTrackingData(travelId, studentTravelId);

        if (trackingData == null) {
            log.warn("[initializeStudentTravelRouteStopTracking] - tracking data provido do redis null");
            return;
        }

        UUID routeStopId = trackingData.routeStopId();
        Double routeStopLatitude = trackingData.routeStopLatitude();
        Double routeStopLongitude = trackingData.routeStopLongitude();
        StudentTravelRouteStopStatus studentTravelRouteStopStatus = StudentTravelRouteStopStatus.EXPECTED;

        InitializeStudentTravelRouteStopEvent event = new InitializeStudentTravelRouteStopEvent(
                studentTravelId,
                travelId,
                routeStopId,
                routeStopLatitude,
                routeStopLongitude,
                studentTravelRouteStopStatus
        );

        eventPublisher.publishEvent(event);

    }

    /*
    * faz o processamento da aproximação do veículo ao ponto de parada do estudante ( add no método principal)
    * */
    public void processRouteStopApproach(UUID travelId, UUID studentTravelId) {
        // recupera os dados de monitoriamento
        StudentTravelRouteStopTrackingCacheDTO trackingData = travelTrackingStaticCache.getStudentTravelTrackingData(travelId, studentTravelId);

        if (trackingData == null) {
            log.warn("[processRouteStopApproach] - tracking data provido do redis null");
            return;
        }

        // recupera a última loc estável do veículo
        LiveLocationDTO lastDriverPosition = redisTrackingService.getLiveLocation(travelId);

        // calcula distância entre ponto de parada e veículo
        DistanceResponseDTO distanceBetweenVehicleAndRouteStop = locationService.distanceBetweenVehicleAndRouteStop(travelId, studentTravelId, lastDriverPosition);

        if (distanceBetweenVehicleAndRouteStop.distance() == null || distanceBetweenVehicleAndRouteStop.distance() < 0) {
            log.warn("[processRouteStopApproach] - distance retornando null ou inválida");
            return;
        }

        Double distance = distanceBetweenVehicleAndRouteStop.distance();

        // se a distancia nao for compatível retorna
        if (distance > APPROACHING_THRESHOLD) {
            return;
        }

        // se nao for igual a "expected" retorna
        if (!trackingData.status().equals(StudentTravelRouteStopStatus.EXPECTED)) {
            return;
        }

        log.info("[processRouteStopApproach] - processamento realizado com sucesso, publicando no listener");

        // monta dto do evento e publica
        ProcessStudentTravelRouteStopApproachingEvent processRouteStopApproachingEvent = new ProcessStudentTravelRouteStopApproachingEvent(studentTravelId, trackingData.studentId(), travelId, trackingData.routeStopId(), distance, Instant.now());

        eventPublisher.publishEvent(processRouteStopApproachingEvent);
    }

    /*
    * realiza a confirmação de que o estudante chegou ao ponto e desembarcou corretamente
    * */
    public void confirmStudentRouteStopReached(UUID travelId, UUID studentTravelId, StudentTravelStatus studentTravelStatus) {
        // cache estático do tracking
        StudentTravelRouteStopTrackingCacheDTO trackingData = travelTrackingStaticCache.getStudentTravelTrackingData(travelId, studentTravelId);

        // recupera o cache armazenado pelos processamentos anteriores
        StudentTravelRouteStopsCacheEvent studentTravelRouteStopMonitoring = redisTrackingService.getStudentTravelRouteStopMonitoring(travelId, studentTravelId);

        StudentTravelRouteStopStatus studentTravelRouteStopStatus = studentTravelRouteStopMonitoring.studentTravelRouteStopStatus();

        // não deve continuar caso o status não seja APPROACHING
        if (!studentTravelRouteStopStatus.equals(StudentTravelRouteStopStatus.APPROACHING)) {
            log.warn("[confirmStudentRouteStopReached] - status retornado do cache não é válido: {}", studentTravelRouteStopStatus);
            return;
        }

        /*
        * faz a validação da evidência de desvínculo com base no status do estudante vinculado à viagem
        * */
        if (!Set.of(StudentTravelStatus.LEFT, StudentTravelStatus.AUTO_DISCONNECTED).contains(studentTravelStatus)) {
            log.warn("[confirmStudentRouteStopReached] - estudante não foi desvinculado ou não saiu da viagem. Status atual: {}", studentTravelStatus);
            return;
        }

        // recupera a última loc estável do veículo
        LiveLocationDTO lastDriverPosition = redisTrackingService.getLiveLocation(travelId);

        // calcula distância entre ponto de parada e veículo
        DistanceResponseDTO distanceBetweenVehicleAndRouteStop = locationService.distanceBetweenVehicleAndRouteStop(travelId, studentTravelId, lastDriverPosition);

        if (distanceBetweenVehicleAndRouteStop.distance() == null || distanceBetweenVehicleAndRouteStop.distance() < 0) {
            log.warn("[confirmStudentRouteStopReached] - distance retornando null ou inválida");
            return;
        }

        Double distance = distanceBetweenVehicleAndRouteStop.distance();

        // se a distancia nao for compatível retorna
        if (distance > REACHED_THRESHOLD) {
            return;
        }

        ConfirmStudentTravelRouteStopReachedEvent confirmStudentTravelRouteStopReachedEvent = new ConfirmStudentTravelRouteStopReachedEvent(
                studentTravelId,
                trackingData.studentId(),
                travelId,
                trackingData.routeStopId(),
                lastDriverPosition.latitude(),
                lastDriverPosition.longitude(),
                distance,
                Instant.now(),
                lastDriverPosition.current_location_timestamp(),
                studentTravelStatus
        );

        // evento atualização redis
        eventPublisher.publishEvent(confirmStudentTravelRouteStopReachedEvent);

        StudentTravelRouteStopDisembarkedEvent studentTravelRouteStopDisembarkedEvent = new StudentTravelRouteStopDisembarkedEvent(
                studentTravelId,
                trackingData.routeStopId(),
                StudentTravelRouteStopStatus.REACHED,
                Instant.now(), // momento que o sistema executou a validação
                Instant.now() // momento que o estudante chegou

        );

        // evento atualização SQL
        eventPublisher.publishEvent(studentTravelRouteStopDisembarkedEvent);
    }

    /*
     * fazer a notificação para os demais eventos
     * listener SQL para o confirm (reached) de forma async (x)
     * realizar o delete do redis ao encerrar a viagem (x)
     * */

    // fazer de viagem cancelled tbm
}
