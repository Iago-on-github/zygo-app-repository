package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.StudentAwayStateCheckEvent;
import com.travel_system.backend_app.exceptions.InactiveAccountException;
import com.travel_system.backend_app.exceptions.TravelException;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.StudentAwayStateDTO;
import com.travel_system.backend_app.model.dtos.StudentTrackingPositionDTO;
import com.travel_system.backend_app.model.dtos.cache.StudentTravelRouteStopTrackingCacheDTO;
import com.travel_system.backend_app.model.dtos.cache.TravelCacheDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveCoordinates;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.response.*;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.GeoPositionRepository;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.repository.TravelRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.travel_system.backend_app.config.constants.GlobalAppConstants.AUTO_DISCONNECT_DISTANCE_METERS;
import static com.travel_system.backend_app.config.constants.GlobalAppConstants.AUTO_DISCONNECT_TIME;


@Service
public class LocationService {

    private final GeoPositionRepository geoPositionRepository;
    private final TravelRepository travelRepository;
    private final StudentTravelRepository studentTravelRepository;

    private final RouteCalculationService routeCalculationService;
    private final TravelService travelService;
    private final RedisTrackingService redisTrackingService;
    private final TravelCacheService travelCacheService;
    private final TravelTrackingNotificationService trackingNotificationService;
    private final StudentTravelRouteStopService studentTravelRouteStopService;

    private final Logger log = LoggerFactory.getLogger(LocationService.class);

    public LocationService(GeoPositionRepository geoPositionRepository, TravelRepository travelRepository, StudentTravelRepository studentTravelRepository, RouteCalculationService routeCalculationService, TravelService travelService, RedisTrackingService redisTrackingService, TravelCacheService travelCacheService, TravelTrackingNotificationService trackingNotificationService, StudentTravelRouteStopService studentTravelRouteStopService) {
        this.geoPositionRepository = geoPositionRepository;
        this.travelRepository = travelRepository;
        this.studentTravelRepository = studentTravelRepository;
        this.routeCalculationService = routeCalculationService;
        this.travelService = travelService;
        this.redisTrackingService = redisTrackingService;
        this.travelCacheService = travelCacheService;
        this.trackingNotificationService = trackingNotificationService;
        this.studentTravelRouteStopService = studentTravelRouteStopService;
    }

    @Transactional
    public void updateStudentPosition(UUID studentTravelId, LiveCoordinates coordinates) {
        if (coordinates.latitude() == null || coordinates.longitude() == null) {
            log.debug("[updateStudentPosition] Dados de Latitude/Longitude são nulos ou inválidos para o estudante: {} ", studentTravelId);
            return;
        }

        applyStudentPositionUpdate(studentTravelId, coordinates);
    }
    
    // verifica se o estudante é compatível para auto-desvinculo
    @Transactional
    public void processStudentAwayState(StudentAwayStateCheckEvent studentAwayStateCheckEvent) {
        UUID travelId = studentAwayStateCheckEvent.travelId();
        LiveLocationDTO liveLocationDTO = studentAwayStateCheckEvent.liveLocationDTO();

        List<DistanceResponseDTO> distanceBetweenPositions = distanceBetweenPositions(travelId, liveLocationDTO);

        List<StudentAwayStateDTO> studentsForAwayState = studentTravelRepository.findStudentsForAwayState(travelId);

        TravelCacheDTO travelStaticCache = travelCacheService.getOrLoadTravelStaticCache(travelId);

        if (travelStaticCache.travelStatus() != TravelStatus.TRAVELLING) {
            throw new TravelException("[processStudentAwayState] Viagem " + travelId + " não está em andamento");
        }

        if (studentsForAwayState == null || studentsForAwayState.isEmpty()) {
            log.warn("[processStudentAwayState] - nenhum estudante encontrado na viagem {} ", travelId);
            return;
        }

        Map<UUID, StudentAwayStateDTO> mapping = new HashMap<>(); // armazena os IDs dos estudantes

        studentsForAwayState.forEach(eachStudent -> mapping.put(eachStudent.studentId(), eachStudent));

        List<UUID> studentTravelsToMarkAway = new ArrayList<>(); // Ids de estudantes que caíram em: AWAY_FROM_BUS
        List<UUID> studentTravelsToAutoDisconnect = new ArrayList<>(); // Ids de estudantes que caíram em: AUTO_DISCONNECTED
        List<UUID> studentTravelsToActive = new ArrayList<>(); // Ids de estudantes que voltaram para: ACTIVE

        // ids de estudantes que estão distantes do ônibus
        Map<UUID, Long> studentsToMarkAway = new HashMap<>();

        // ids de estudantes que irão ser limpos no redis
        Set<UUID> studentIdsToClear = new HashSet<>();

        Set<UUID> studentIdsToAutoDisconnect = new HashSet<>(); // students auto-disconnected

        Map<UUID, Long> awayStudents = redisTrackingService.getStudentAwayTimestamp(travelId);

        distanceBetweenPositions.forEach(dist -> {
            StudentAwayStateDTO student = mapping.get(dist.studentId());

            if (student == null) {
                log.warn("[processStudentAwayState] - estudante {} ignorado, não passou na validação para a viagem {} ", dist.studentId(), travelId );
                return;
            }

            if (!student.embark()) {
                log.warn("[processStudentAwayState] - estudante {} não está na viagem.", student.studentId());
                return;
            }

            if (student.studentTravelStatus() == StudentTravelStatus.LEFT) {
                log.warn("[processStudentAwayState] - estudante {} está com o Status LEFT", student.studentId());
                return;
            }

            if (student.studentTravelStatus() == StudentTravelStatus.AUTO_DISCONNECTED) {
                log.warn("[processStudentAwayState] - estudante {} stá com o Status AUTO_DISCONNECTED", student.studentId());
                return;
            }

            // verifica se ja tem timestamp
            Long studentAwayTimestamp = awayStudents.get(student.studentId());

            if (dist.distance() >= AUTO_DISCONNECT_DISTANCE_METERS) {
                log.info("[processStudentAwayState] - distância do estudante maior do que a distância minima permitida");

                if (student.studentTravelStatus() != StudentTravelStatus.AWAY_FROM_BUS) {
                    studentTravelsToMarkAway.add(student.studentTravelId());
                }

                if (studentAwayTimestamp != null) {
                    log.info("[processStudentAwayState] - estudante possui timestamp no redis");

                    long timeNow = Instant.now().toEpochMilli();
                    long awayTimeMillis = timeNow - studentAwayTimestamp;

                    log.info("[processStudentAwayState] - debug de time: redisTimestamP: {}, awayTimeMillisResult: {}, AUTO_DISCONNECT_TIME: {}", studentAwayTimestamp, awayTimeMillis, AUTO_DISCONNECT_TIME);

                    if (awayTimeMillis >= AUTO_DISCONNECT_TIME) {
                        log.info("[processStudentAwayState] - estudante desembarcou. Começando desvinculação automática dele");
                        studentTravelsToAutoDisconnect.add((student.studentTravelId()));

                        studentIdsToAutoDisconnect.add(student.studentId());

                        studentIdsToClear.add(student.studentId());

                    }

                } else {
                    log.info("[processStudentAwayState] - estudante não possui timestamp no redis");

                    long timestampStudentAsAway = Instant.now().toEpochMilli();
                    studentsToMarkAway.put(student.studentId(), timestampStudentAsAway);
                }
            } else {
                log.info("[processStudentAwayState] - estudante não atende mais as regras do auto-desvinculo. Limpando redis");

                if (student.studentTravelStatus() != StudentTravelStatus.ACTIVE) {
                    studentTravelsToActive.add(student.studentTravelId());
                }

                studentIdsToClear.add(student.studentId());
            }
        });

        // UPDATES EM LOTE - REDIS E BANCO - atualiza tudo uma única vez com base nos dados armazenados dentro do loop

        // limpa o registro dos estudantes com base no ID da viagem
        redisTrackingService.clearStudentAwayState(travelId, studentIdsToClear);

        // armazena timestamps de estudantes que estão distantes do ônibus
        redisTrackingService.markStudentAsAway(travelId, studentsToMarkAway);

        // realiza update no banco com base na List de cada um
        if (!studentTravelsToMarkAway.isEmpty()) studentTravelRepository.updateStudentTravelStatus(studentTravelsToMarkAway, StudentTravelStatus.AWAY_FROM_BUS);
        if (!studentTravelsToAutoDisconnect.isEmpty()) studentTravelRepository.updateStudentTravelStatus(studentTravelsToAutoDisconnect, StudentTravelStatus.AUTO_DISCONNECTED);
        if (!studentTravelsToActive.isEmpty()) studentTravelRepository.updateStudentTravelStatus(studentTravelsToActive, StudentTravelStatus.ACTIVE);

        // caso haja estudantes compatíveis com auto-disconnect, desvincula da viagem
        if (!studentTravelsToAutoDisconnect.isEmpty()) {
            Instant disembarkHour = Instant.now();
            studentTravelRepository.disconnectedStudentFromTrip(studentTravelsToAutoDisconnect, StudentTravelStatus.AUTO_DISCONNECTED, disembarkHour, false);

            // apenas se conseguir desconectar: busca a viagem inteira no banco para recuperar dados para notificação
            Travel travel = travelRepository.findById(travelId)
                    .orElseThrow(() -> new EntityNotFoundException("Viagem não encontrada: " + travelId));

            // processamento p/ cada estudante desvinculado
            studentIdsToAutoDisconnect.forEach(studentId -> {
                /*
                * algoritmo que detecta se o estudante desembarcou no RouteStop dele após ele sair da viagem
                * */
                studentTravelRouteStopService.confirmStudentRouteStopReached(travelId, studentId, StudentTravelStatus.AUTO_DISCONNECTED);

                // manda notificação
                trackingNotificationService.sendAutoDisconnectStudentNotification(travel, studentId);
            });

        }
    }

    // distância entre o motorista e o estudante
    protected List<DistanceResponseDTO> distanceBetweenPositions(UUID travelId, LiveLocationDTO driverPosition) {
        long start = System.currentTimeMillis(); // debugging ttl

        Set<StudentTrackingPositionDTO> linkedStudentTravel = travelService.linkedStudentTravel(travelId);

        log.info("Viagem {}: Iniciando cálculo de distância para {} alunos vinculados.", travelId, linkedStudentTravel.size());

        List<DistanceResponseDTO> results = linkedStudentTravel.stream()
                .filter(student -> {
                    boolean hasPosition = student.latitude() != null && student.longitude() != null;
                    if (!hasPosition) {
                        log.warn("Aluno {} ignorado: Posição (GeoPosition) está nula no banco.", student.studentId());
                    }
                    return hasPosition;
                })
                .map(student -> {

                    double distance = routeCalculationService.calculateHaversineDistanceInMeters(
                            driverPosition.latitude(),
                            driverPosition.longitude(),
                            student.latitude(),
                            student.longitude()
                    );

                    return new DistanceResponseDTO(student.studentId(), distance);
                }).toList();

        long executingTime = System.currentTimeMillis() - start;

        log.info("[distanceBetweenPositions] Viagem {}: Cálculo concluído. {} alunos processados com sucesso. TTL: {}", travelId, results.size(), executingTime);
        return results;
    }

    private void applyStudentPositionUpdate(UUID studentTravelId, LiveCoordinates actually) {
        StudentTravel studentTravel = studentTravelRepository.findById(studentTravelId)
                .orElseThrow(() -> new EntityNotFoundException("Entidade StudentTravel não encontrada: " + studentTravelId));

        GeoPosition anterior = studentTravel.getPosition();

        // primeiro ping, não há deslocamento
        if (anterior == null) {
            log.info("[LocationService] - Primeiro ping do estudante {}, salvando position.", studentTravelId);

            GeoPosition newPosition = new GeoPosition();

            newPosition.setLatitude(actually.latitude());
            newPosition.setLongitude(actually.longitude());
            newPosition.setTimeStamp(Instant.now());
            newPosition.setStudentTravel(studentTravel);

            studentTravel.setPosition(newPosition);

            geoPositionRepository.save(newPosition);

            return;
        }

        // retorna se há deslocamento
        Boolean displacementDetected = isStudentDisplacement(anterior, actually);

        if (displacementDetected) {
            log.info("[LocationService] - Houve deslocamento para o estudante {}, salvando position.", studentTravelId);

            anterior.setLatitude(actually.latitude());
            anterior.setLongitude(actually.longitude());
            anterior.setTimeStamp(Instant.now());

            studentTravel.setPosition(anterior);

        }

    }

    private Boolean isStudentDisplacement(GeoPosition anteriorPosition, LiveCoordinates actuallyPosition) {
        Double calculateHaversineDistance = routeCalculationService.calculateHaversineDistanceInMeters(
                actuallyPosition.latitude(),
                actuallyPosition.longitude(),
                anteriorPosition.getLatitude(),
                anteriorPosition.getLongitude());

        Double DISPLACEMENT_METERS_TOLERANCE = 3.0;
        return calculateHaversineDistance > DISPLACEMENT_METERS_TOLERANCE;
    }

}
