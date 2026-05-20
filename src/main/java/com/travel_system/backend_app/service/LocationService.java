package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.GeoPosition;
import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveCoordinates;
import com.travel_system.backend_app.repository.GeoPositionRepository;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class LocationService {

    private final GeoPositionRepository geoPositionRepository;
    private final StudentTravelRepository studentTravelRepository;
    private final RouteCalculationService routeCalculationService;

    private Logger logger = LoggerFactory.getLogger(LocationService.class);

    public LocationService(GeoPositionRepository geoPositionRepository, StudentTravelRepository studentTravelRepository, RouteCalculationService routeCalculationService) {
        this.geoPositionRepository = geoPositionRepository;
        this.studentTravelRepository = studentTravelRepository;
        this.routeCalculationService = routeCalculationService;
    }

    @Transactional
    public void updateStudentPosition(UUID studentTravelId, LiveCoordinates coordinates) {
        if (coordinates.latitude() == null || coordinates.longitude() == null) {
            logger.debug("[updateStudentPosition] Dados de Latitude/Longitude são nulos ou inválidos para o estudante: {} ", studentTravelId);
            return;
        }

        applyStudentPositionUpdate(studentTravelId, coordinates);
    }

    private void applyStudentPositionUpdate(UUID studentTravelId, LiveCoordinates actually) {
        StudentTravel studentTravel = studentTravelRepository.findById(studentTravelId)
                .orElseThrow(() -> new EntityNotFoundException("Entidade StudentTravel não encontrada: " + studentTravelId));

        GeoPosition anterior = studentTravel.getPosition();

        // primeiro ping, não há deslocamento
        if (anterior == null) {
            logger.info("[LocationService] - Primeiro ping do estudante {}, salvando position.", studentTravelId);

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
            logger.info("[LocationService] - Houve deslocamento para o estudante {}, salvando position.", studentTravelId);

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
