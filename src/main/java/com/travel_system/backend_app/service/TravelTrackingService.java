package com.travel_system.backend_app.service;


import com.travel_system.backend_app.events.NewLocationReceivedEvents;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.mapboxApi.*;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.route.LocationPointDTO;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.repository.TravelLocationHistoryRepository;
import com.travel_system.backend_app.repository.TravelRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TravelTrackingService {

    private final TravelRepository travelRepository;
    private final RedisTrackingService redisTrackingService;
    private final MapboxAPIService mapboxAPIService;
    private final RouteCalculationService routeCalculationService;
    private final StudentTravelRepository studentTravelRepository;
    private final GpsDataIngestorService gpsDataIngestorService;
    private final TravelLocationHistoryRepository travelLocationHistoryRepository;

    private final ApplicationEventPublisher eventPublisher;

    // usar no lugar de Instant.now() para ajudar nos testes unitários
    private final Clock clock;

    public TravelTrackingService(TravelRepository travelRepository, RedisTrackingService redisTrackingService, MapboxAPIService mapboxAPIService, RouteCalculationService routeCalculationService, StudentTravelRepository studentTravelRepository, GpsDataIngestorService gpsDataIngestorService, TravelLocationHistoryRepository travelLocationHistoryRepository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.travelRepository = travelRepository;
        this.redisTrackingService = redisTrackingService;
        this.mapboxAPIService = mapboxAPIService;
        this.routeCalculationService = routeCalculationService;
        this.studentTravelRepository = studentTravelRepository;
        this.gpsDataIngestorService = gpsDataIngestorService;
        this.travelLocationHistoryRepository = travelLocationHistoryRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // Anota que o motorista passou pela localização atual e libera o celular o mais rápido possível
    public void markDriverCheckpoint(UUID cityId, UUID travelId, VehicleLocationRequestDTO vehicleLocationRequest) {
        Double latitude = vehicleLocationRequest.latitude();
        Double longitude = vehicleLocationRequest.longitude();
        Double speed = vehicleLocationRequest.speed();
        Double heading = vehicleLocationRequest.heading();

        Travel travel = travelRepository.findById(travelId)
                .orElseThrow(() -> new TripNotFound("Trip not found"));

        if (travel.getTravelStatus() != TravelStatus.TRAVELLING) {
            throw new TravelException("A viagem não está em andamento");
        }

        // salva no redis como última posição conhecida matendo a distance e o geometry antigos
        LiveLocationDTO liveLocation = redisTrackingService.getLiveLocation(String.valueOf(travelId));

        // Se liveLocation for null (primeiro ping)
        Double distance = (liveLocation != null) ? liveLocation.distance() : null;
        String geometry = (liveLocation != null) ? liveLocation.geometry() : null;

        String strLatitude = String.valueOf(latitude);
        String strLongitude = String.valueOf(longitude);
        redisTrackingService.storeLiveLocation(String.valueOf(travelId), strLatitude, strLongitude, distance, geometry);

        // dispara evento de domínio
        NewLocationReceivedEvents event = new NewLocationReceivedEvents(
                travelId,
                latitude,
                longitude,
                Instant.now(),
                travel.getTravelStatus(),
                speed, heading);

        eventPublisher.publishEvent(event);

        gpsDataIngestorService.sendVehicleGps(cityId.toString(), travelId.toString(), new VehicleLocationRequestDTO(travelId, latitude, longitude, speed, heading));
    }

    // Orquestra o sistema de tracking em tempo real, verificando desvios de rota,
    // recalculando o ETA e salvando a localização e os metadados da viagem no Redis
    public void processNewLocation(VehicleLocationRequestDTO vehicleLocationRequest) {
        UUID travelId = vehicleLocationRequest.travelId();
        Double currentLat = vehicleLocationRequest.latitude();
        Double currentLng = vehicleLocationRequest.longitude();

        Travel travel = travelRepository.findById(travelId)
                .orElseThrow(() -> new TripNotFound("Trip not found"));

        if (travel.getTravelStatus() != TravelStatus.TRAVELLING) {
            throw new TravelException("A viagem não está em andamento");
        }

        RouteDeviationDTO routeDeviation = routeCalculationService.isRouteDeviation(currentLat, currentLng, travel.getPolylineRoute());

        RouteDetailsDTO newEtaRecalculateByApi;
        PreviousStateDTO previousEta;

        double newETARecalculateByInternally;

        Double currentDuration;
        Double currentDistance;
        String currentPolyline;

        try {
            // se está fora da rota, chama o metodo para recalcular a distância entre os pontos
            if (routeDeviation.isOffRoute()) {
                newEtaRecalculateByApi = mapboxAPIService.recalculateETA(
                        currentLng,
                        currentLat,
                        travel.getFinalLongitude(),
                        travel.getFinalLatitude());

                currentDuration = newEtaRecalculateByApi.duration();
                currentDistance = newEtaRecalculateByApi.distance();
                currentPolyline = newEtaRecalculateByApi.geometry();

            } else {
                previousEta = redisTrackingService.getPreviousEta(travel.getId().toString());

                long currentTimeMillis = clock.millis();
                long timeElapsedMillis = currentTimeMillis - previousEta.timeStamp();
                double timeElapsedSeconds = (double) timeElapsedMillis / 1000.0;

                newETARecalculateByInternally = previousEta.durationRemaining() - timeElapsedSeconds;

                // nunca deixa ser valor negativo
                newETARecalculateByInternally = Math.max(0.0, newETARecalculateByInternally);

                currentDuration = newETARecalculateByInternally;
                currentDistance = travel.getDistance();
                currentPolyline = travel.getPolylineRoute();
            }
        } catch (Exception e) {
            throw new RecalculateEtaException(e.getMessage(), e.getCause());
        }

        redisTrackingService.storeLiveLocation(
                travel.getId().toString(),
                currentLat.toString(),
                currentLng.toString(),
                currentDuration,
                currentPolyline);

        redisTrackingService.storeTravelMetadata(
                travel.getId().toString(),
                currentDuration,
                currentDistance,
                travel.getTravelStatus().toString()
        );
    }

    // haverá um popup no front que perguntará se o estudante irá participar da viagem
    public void confirmEmbarkOnTravel(UUID studentId, UUID travelId) {
        StudentTravel studentTravel = studentTravelRepository
                .findByStudentIdAndTravelId(studentId, travelId)
                .orElseThrow(() -> new TravelStudentAssociationNotFoundException("Associação travel e student não encontrada"));

        if (studentTravel.isEmbark()) {
            throw new BoardingAlreadyConfirmedException("Embarque já confirmado");
        }

        studentTravel.setEmbark(true);
        studentTravelRepository.save(studentTravel);
    }

    // endpoint de fastview - provê a loc do driver
    public LiveLocationDTO getDriverPosition(UUID travelId) {
        Travel travel = travelRepository.findById(travelId).orElseThrow(() -> new EntityNotFoundException("Viagem não encontrada: " + travelId));

        if (!(travel.getTravelStatus() == TravelStatus.TRAVELLING)) {
            throw new TravelException("Viagem " + travelId + " não está em andamento.");
        }

        LiveLocationDTO liveCoordinates = extractLiveCoordinates(travelId);

        String geometry = liveCoordinates.geometry();
        double distance = liveCoordinates.distance();
        Double lastCalcLatitude = liveCoordinates.lastCalcLat();
        Double lastCalcLongitude = liveCoordinates.lastCalcLng();

        RouteDeviationDTO isDeviation = routeCalculationService.isRouteDeviation(
                liveCoordinates.lastCalcLat(),
                liveCoordinates.lastCalcLng(),
                geometry);

        RouteDetailsDTO routeDetailsDTO;

        if (geometry == null || isDeviation.isOffRoute()) {
            routeDetailsDTO = mapboxAPIService.calculateRoute(
                    liveCoordinates.longitude(),
                    liveCoordinates.latitude(),
                    travel.getFinalLongitude(),
                    travel.getFinalLatitude());

            geometry = routeDetailsDTO.geometry();
            distance = routeDetailsDTO.distance();

            lastCalcLatitude = liveCoordinates.latitude();
            lastCalcLongitude = liveCoordinates.longitude();

            redisTrackingService.storeLiveLocation(
                    String.valueOf(travelId),
                    String.valueOf(lastCalcLatitude),
                    String.valueOf(lastCalcLongitude),
                    distance,
                    geometry);
        }

        return new LiveLocationDTO(
                liveCoordinates.latitude(),
                liveCoordinates.longitude(),
                geometry,
                distance,
                lastCalcLatitude,
                lastCalcLongitude);
    }

    // fornece um histórico de points salvos no banco
    public Page<LocationPointDTO> getTravelHistory(UUID travelId) {
        Pageable pageable = PageRequest.of(0, 100);

        return travelLocationHistoryRepository.findLatLongByTravelIdAsc(travelId, pageable);
    }

    // MÉTODOS AUXILIARES
    private LiveLocationDTO extractLiveCoordinates(UUID travelId) {
        LiveLocationDTO currentLocation = redisTrackingService.getLiveLocation(String.valueOf(travelId));

        try {
            double currentLatitude = currentLocation.latitude();
            double currentLongitude = currentLocation.longitude();

            return new LiveLocationDTO(
                    currentLatitude,
                    currentLongitude,
                    currentLocation.geometry(),
                    currentLocation.distance(),
                    currentLocation.lastCalcLat(),
                    currentLocation.lastCalcLng());
        } catch (Exception e) {
            throw new LiveLocationDataNotFoundException("Dados de rastreamento corrompidos ou inválidos: " + e.getMessage());
        }
    }
}
