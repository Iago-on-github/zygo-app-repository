package com.travel_system.backend_app.service;


import com.travel_system.backend_app.events.NewLocationReceivedEvents;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.mapboxApi.*;
import com.travel_system.backend_app.model.dtos.request.RouteDeviationRequestDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.route.LocationPointDTO;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.repository.TravelLocationHistoryRepository;
import com.travel_system.backend_app.repository.TravelRepository;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.jdbc.BatchedTooManyRowsAffectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.security.InvalidParameterException;
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

    private final Logger logger = LoggerFactory.getLogger(TravelTrackingService.class);

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
        if (!travelId.equals(vehicleLocationRequest.travelId())) {
            throw new IllegalStateException("TravelID da URL diferente do body");
        }

        if (cityId == null || travelId == null) {
            throw new EmptyMandatoryFieldsFound("[markDriverCheckpoint] CityId: " + cityId + " ou TravelId " + travelId + " são obrigatorios.");
        }

        if (vehicleLocationRequest == null || vehicleLocationRequest.latitude() == null || vehicleLocationRequest.longitude() == null) {
            throw new NoSuchCoordinates("[markDriverCheckpoint] vehicleLocationRequest null ou dados de lat/lng null para a viagem: "
                    + travelId + " . DTO: " + vehicleLocationRequest);
        }

        Double latitude = vehicleLocationRequest.latitude();
        Double longitude = vehicleLocationRequest.longitude();
        Double speed = vehicleLocationRequest.speed();
        Double heading = vehicleLocationRequest.heading();

        Travel travel = travelRepository.findById(travelId)
                .orElseThrow(() -> new TripNotFound("Trip not found"));

        if (travel.getTravelStatus() != TravelStatus.TRAVELLING) {
            throw new TravelException("A viagem " + travelId + " não está em andamento");
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
                speed,
                heading);

        eventPublisher.publishEvent(event);

        gpsDataIngestorService.sendVehicleGps(cityId.toString(), travelId.toString(), new VehicleLocationRequestDTO(travelId, latitude, longitude, speed, heading));
    }

    // Orquestra o sistema de tracking em tempo real, verificando desvios de rota,
    // recalculando o ETA e salvando a localização e os metadados da viagem no Redis
    public void processNewLocation(VehicleLocationRequestDTO vehicleLocationRequest) {
        if (vehicleLocationRequest == null || vehicleLocationRequest.travelId() == null || vehicleLocationRequest.latitude() == null || vehicleLocationRequest.longitude() == null) {
            throw new EmptyMandatoryFieldsFound("[processNewLocation] campos de entrada obrigatórios null ou inválidos: " + vehicleLocationRequest);
        }

        UUID travelId = vehicleLocationRequest.travelId();
        Double currentLat = vehicleLocationRequest.latitude();
        Double currentLng = vehicleLocationRequest.longitude();

        Travel travel = travelRepository.findById(travelId)
                .orElseThrow(() -> new TripNotFound("[processNewLocation] Trip not found: " + travelId));

        if (travel.getTravelStatus() != TravelStatus.TRAVELLING) {
            throw new TravelException("[processNewLocation] A viagem não está em andamento: " + travelId);
        }

        RouteDeviationDTO routeDeviation = routeCalculationService.isRouteDeviation(new RouteDeviationRequestDTO(travelId, currentLat, currentLng));

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

                if (newEtaRecalculateByApi == null
                        || newEtaRecalculateByApi.duration() == null
                        || newEtaRecalculateByApi.distance() == null) {
                    throw new RecalculateEtaException("[processNewLocation] resposta inválida da API de rotas");
                }

                currentDuration = newEtaRecalculateByApi.duration();
                currentDistance = newEtaRecalculateByApi.distance();
                currentPolyline = newEtaRecalculateByApi.geometry();

            } else {
                previousEta = redisTrackingService.getPreviousEta(travel.getId().toString());

                if (previousEta == null || previousEta.timeStamp() == null || previousEta.durationRemaining() == null) {
                    throw new EtaDataStatesInvalidException("[processNewLocation] dados do previousEta inválidos ou null para a viagem: " + previousEta);
                }

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
        } catch (RecalculateEtaException | EtaDataStatesInvalidException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RecalculateEtaException(e.getMessage());
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
        if (studentId == null || travelId == null) {
            throw new EmptyMandatoryFieldsFound("[confirmEmbarkOnTravel] parâmetros obrigatórios null ou inválidos para a viagem: " + travelId);
        }

        StudentTravel studentTravel = studentTravelRepository
                .findByStudentIdAndTravelId(studentId, travelId)
                .orElseThrow(() -> new TravelStudentAssociationNotFoundException("[confirmEmbarkOnTravel] Associação travel e student não encontrada. Viagem: " + travelId));

        if (studentTravel.isEmbark()) {
            throw new BoardingAlreadyConfirmedException("[confirmEmbarkOnTravel] Embarque já confirmado. Viagem: " + travelId);
        }

        studentTravel.setEmbark(true);
        studentTravelRepository.save(studentTravel);

        logger.info("[confirmEmbarkOnTravel] embarque do estudante: {} confirmado com sucesso para a viagem: {} ", studentId, travelId);
    }

    // endpoint de fastview - provê a loc do driver
    public LiveLocationDTO getDriverPosition(UUID travelId) {
        if (travelId == null) {
            throw new EmptyMandatoryFieldsFound("[getDriverPosition] dados de parâmetro inválidos ou null");
        }

        Travel travel = travelRepository.findById(travelId)
                .orElseThrow(() -> new TripNotFound("[getDriverPosition] Viagem não encontrada: " + travelId));

        if (!(travel.getTravelStatus() == TravelStatus.TRAVELLING)) {
            throw new TravelException("[getDriverPosition] Viagem " + travelId + " não está em andamento.");
        }

        LiveLocationDTO liveCoordinates = extractLiveCoordinates(travelId);

        String geometry = liveCoordinates.geometry();
        double distance = liveCoordinates.distance();
        Double lastCalcLatitude = liveCoordinates.lastCalcLat();
        Double lastCalcLongitude = liveCoordinates.lastCalcLng();

        RouteDeviationDTO isDeviation = routeCalculationService
                .isRouteDeviation(new RouteDeviationRequestDTO(travelId, liveCoordinates.lastCalcLat(), liveCoordinates.lastCalcLng()));

        RouteDetailsDTO routeDetailsDTO;

        if (geometry == null || isDeviation.isOffRoute()) {
            routeDetailsDTO = mapboxAPIService.calculateRoute(
                    liveCoordinates.longitude(),
                    liveCoordinates.latitude(),
                    travel.getFinalLongitude(),
                    travel.getFinalLatitude());

            if (routeDetailsDTO == null ||
            routeDetailsDTO.duration() == null ||
            routeDetailsDTO.distance() == null ||
            routeDetailsDTO.geometry() == null) {
                throw new RecalculateEtaException("[getDriverPosition] resposta inválida da api de rotas: " + routeDetailsDTO);
            }

            logger.info("[getDriverPosition] recalculo de ROTA - API respondeu com dados válidos, começando processamento de dados no Redis. Viagem: {} ", travelId);

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

        logger.info("[getDriverPosition] retornando dados processados com o LiveLocationDTO para a viagem: {} ", travelId);

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
        if (travelId == null) {
            throw new EmptyMandatoryFieldsFound("[getTravelHistory] Dados de parâmetros inválidos ou não encontrados");
        }

        Pageable pageable = PageRequest.of(0, 100);

        return travelLocationHistoryRepository.findLatLongByTravelIdAsc(travelId, pageable);
    }

    // MÉTODOS AUXILIARES
    private LiveLocationDTO extractLiveCoordinates(UUID travelId) {
        LiveLocationDTO currentLocation = redisTrackingService.getLiveLocation(String.valueOf(travelId));

        logger.info("currentLocation: {}", currentLocation);

        if (currentLocation == null ||
                currentLocation.lastCalcLat() == null ||
                currentLocation.lastCalcLng() == null ||
                currentLocation.latitude() == null ||
                currentLocation.longitude() == null ||
                currentLocation.distance() == null) {
            throw new LiveLocationDataNotFoundException("[extractLiveCoordinates] Dados obrigatórios do liveLocation são null ou inválidos. Viagem: " + travelId);
        }

        double currentLatitude = currentLocation.latitude();
        double currentLongitude = currentLocation.longitude();

        return new LiveLocationDTO(
                currentLatitude,
                currentLongitude,
                currentLocation.geometry(),
                currentLocation.distance(),
                currentLocation.lastCalcLat(),
                currentLocation.lastCalcLng()
        );

    }
}
