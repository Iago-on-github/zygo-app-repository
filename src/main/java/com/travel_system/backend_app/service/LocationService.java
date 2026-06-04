package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.TravelException;
import com.travel_system.backend_app.model.GeoPosition;
import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveCoordinates;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.response.DistanceResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentTravelResponseDTO;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;
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


@Service
public class LocationService {

    private final GeoPositionRepository geoPositionRepository;
    private final TravelRepository travelRepository;
    private final StudentTravelRepository studentTravelRepository;

    private final RouteCalculationService routeCalculationService;
    private final TravelService travelService;
    private final RedisTrackingService redisTrackingService;
    
    private Logger log = LoggerFactory.getLogger(LocationService.class);

    private static final double AUTO_DISCONNECT_DISTANCE_METERS = 350;
    private static final long AUTO_DISCONNECT_TIME = TimeUnit.MINUTES.toMillis(5);

    public LocationService(GeoPositionRepository geoPositionRepository, StudentTravelRepository studentTravelRepository, RouteCalculationService routeCalculationService, TravelService travelService, TravelRepository travelRepository, RedisTrackingService redisTrackingService) {
        this.geoPositionRepository = geoPositionRepository;
        this.studentTravelRepository = studentTravelRepository;
        this.routeCalculationService = routeCalculationService;
        this.travelService = travelService;
        this.travelRepository = travelRepository;
        this.redisTrackingService = redisTrackingService;
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
    public void processStudentAwayState(UUID travelId, LiveLocationDTO liveLocationDTO) {
        List<DistanceResponseDTO> distanceBetweenPositions = distanceBetweenPositions(travelId, liveLocationDTO);

        Travel travel = travelRepository.findById(travelId)
                .orElseThrow(() -> new EntityNotFoundException("Viagem " + travelId + " não encontrada."));

        if (travel.getTravelStatus() != TravelStatus.TRAVELLING) {
            throw new TravelException("[processStudentAwayState] Viagem " + travelId + " não está em andamento");
        }

        if (travel.getStudentTravels() == null) {
            log.warn("[processStudentAwayState] - nenhum estudante encontrado na viagem {} ", travelId);
            return;
        }

        distanceBetweenPositions.forEach(dist -> {

            Optional<StudentTravel> studentTravelOptional = travel.getStudentTravels().stream()
                    .filter(st -> st.getStudent() != null
                            && Objects.equals(st.getStudent().getId(), dist.studentId())
                            && st.getPosition() != null
                            && st.isEmbark()
                            && StudentTravelStatus.AUTO_DISCONNECTED != st.getStudentTravelStatus()
                            && StudentTravelStatus.LEFT != st.getStudentTravelStatus())
                    .findFirst();

            if (studentTravelOptional.isEmpty()) {
                log.warn("[processStudentAwayState] - estudante {} ignorado, não passou na validação para a viagem {} ", dist.studentId(), travelId );
                return;
            }

            StudentTravel studentTravel = studentTravelOptional.get();

            String studentEmail = studentTravel.getStudent().getEmail();

            // verifica se ja tem timestamp
            Long studentAwayTimestamp = redisTrackingService.getStudentAwayTimestamp(travelId, dist);

            if (dist.distance() >= AUTO_DISCONNECT_DISTANCE_METERS) {
                log.info("[processStudentAwayState] - distância do estudante maior do que a distância minima permitida");
                studentTravel.setStudentTravelStatus(StudentTravelStatus.AWAY_FROM_BUS);

                if (studentAwayTimestamp != null) {
                    log.info("[processStudentAwayState] - estudante possui timestamp no redis");

                    long timeNow = Instant.now().toEpochMilli();
                    long awayTimeMillis = timeNow - studentAwayTimestamp;

                    if (awayTimeMillis >= AUTO_DISCONNECT_TIME){
                        log.info("[processStudentAwayState] - estudante desembarcou. Começando desvinculação automática dele");

                        studentTravel.setStudentTravelStatus(StudentTravelStatus.AUTO_DISCONNECTED);

                        travelService.leaveTravel(travelId, studentEmail, StudentTravelStatus.AUTO_DISCONNECTED);

                        redisTrackingService.clearStudentAwayState(travelId, dist);
                    }

                } else {
                    log.info("[processStudentAwayState] - estudante não possui timestamp no redis");

                    redisTrackingService.markStudentAsAway(travelId, dist);

                    studentTravelRepository.save(studentTravel);
                }
            } else {
                log.info("[processStudentAwayState] - estudante não atende mais as regras do auto-desvinculo. Limpando redis");

                studentTravel.setStudentTravelStatus(StudentTravelStatus.ACTIVE);
                redisTrackingService.clearStudentAwayState(travelId, dist);

                studentTravelRepository.save(studentTravel);
            }
        });
    }
    
    // distância entre o motorista e o estudante
    protected List<DistanceResponseDTO> distanceBetweenPositions(UUID travelId, LiveLocationDTO driverPosition) {
        Set<StudentTravelResponseDTO> linkedStudentTravel = travelService.linkedStudentTravel(travelId);

        log.info("Viagem {}: Iniciando cálculo de distância para {} alunos vinculados.", travelId, linkedStudentTravel.size());

        List<DistanceResponseDTO> results = linkedStudentTravel.stream()
                .filter(student -> {
                    boolean hasPosition = student.position() != null;
                    if (!hasPosition) {
                        log.warn("Aluno {} ignorado: Posição (GeoPosition) está nula no banco.", student.studentId());
                    }
                    return hasPosition;
                })
                .map(student -> {

                    double distance = routeCalculationService.calculateHaversineDistanceInMeters(
                            driverPosition.latitude(),
                            driverPosition.longitude(),
                            student.position().getLatitude(),
                            student.position().getLongitude()
                    );

                    return new DistanceResponseDTO(student.studentId(), distance);
                })
                .toList();
        log.info("Viagem {}: Cálculo concluído. {} alunos processados com sucesso.", travelId, results.size());
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
