package com.travel_system.backend_app.service;


import com.travel_system.backend_app.events.NewLocationReceivedEvents;
import com.travel_system.backend_app.events.StudentAwayStateCheckEvent;
import com.travel_system.backend_app.events.VehicleGpsMessageDTO;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.model.GeoPosition;
import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.mapboxApi.*;
import com.travel_system.backend_app.model.dtos.request.RouteDeviationRequestDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.response.DistanceResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentTravelResponseDTO;
import com.travel_system.backend_app.model.dtos.response.TravelCacheDTO;
import com.travel_system.backend_app.model.dtos.route.LocationPointDTO;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.repository.TravelLocationHistoryRepository;
import com.travel_system.backend_app.repository.TravelRepository;
import jakarta.persistence.EntityNotFoundException;
import org.checkerframework.checker.units.qual.Current;
import org.hibernate.jdbc.BatchedTooManyRowsAffectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.xml.stream.Location;
import java.awt.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final TravelService travelService;
    private final LocationService locationService;
    private final TravelCacheService travelCacheService;

    private final Logger logger = LoggerFactory.getLogger(TravelTrackingService.class);

    private final ApplicationEventPublisher eventPublisher;

    private static final double ROUTE_RECALCULATION_THRESHOLD = 50.0;

    // usar no lugar de Instant.now() para ajudar nos testes unitários
    private final Clock clock;

    public TravelTrackingService(TravelRepository travelRepository, RedisTrackingService redisTrackingService, MapboxAPIService mapboxAPIService, RouteCalculationService routeCalculationService, StudentTravelRepository studentTravelRepository, GpsDataIngestorService gpsDataIngestorService, TravelLocationHistoryRepository travelLocationHistoryRepository, TravelService travelService, LocationService locationService, TravelCacheService travelCacheService, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.travelRepository = travelRepository;
        this.redisTrackingService = redisTrackingService;
        this.mapboxAPIService = mapboxAPIService;
        this.routeCalculationService = routeCalculationService;
        this.studentTravelRepository = studentTravelRepository;
        this.gpsDataIngestorService = gpsDataIngestorService;
        this.travelLocationHistoryRepository = travelLocationHistoryRepository;
        this.travelService = travelService;
        this.locationService = locationService;
        this.travelCacheService = travelCacheService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // Anota que o motorista passou pela localização atual e libera o celular o mais rápido possível
    public void markDriverCheckpoint(UUID cityId, UUID travelId, VehicleLocationRequestDTO vehicleLocationRequest) {
        if (!travelId.equals(vehicleLocationRequest.travelId())) {
            throw new IllegalStateException("TravelID da URL diferente do body");
        }

        // busca por cache da viagem, caso não haja, faz requisição e armazena os dados em cache para utilizar aqui
        TravelCacheDTO travelCached = travelCacheService.getOrLoadTravelStaticCache(travelId);

        if (travelCached.travelStatus() != TravelStatus.TRAVELLING) {
            throw new TravelException("A viagem " + travelId + " não está em andamento");
        }

        Double latitude = vehicleLocationRequest.latitude();
        Double longitude = vehicleLocationRequest.longitude();
        Double speed = vehicleLocationRequest.speed();
        Double heading = vehicleLocationRequest.heading();

        // pings real time
        redisTrackingService.storeCurrentLocation(travelId, new CurrentVehicleLocationDTO(latitude, longitude, speed, heading));

        // salva no redis como última posição conhecida matendo a distance e o geometry antigos
        RouteCalculationReferenceDTO routeCalculateReference = redisTrackingService.getRouteCalculateReference(travelId);
        RouteDetailsDTO routeState = redisTrackingService.getRouteState(travelId);

        String strLatitude = String.valueOf(latitude);
        String strLongitude = String.valueOf(longitude);

        Double finalLongitude = travelCached.finalLongitude();
        Double finalLatitude = travelCached.finalLatitude();

        // realiza o primeiro cálculo da viagem
        if (routeCalculateReference == null || routeCalculateReference.lastCalcLat() == null || routeCalculateReference.lastCalcLng() == null) {
            RouteDetailsDTO routeDetailsDTO = mapboxAPIService.recalculateETA(longitude, latitude, finalLongitude, finalLatitude);

            if (routeDetailsDTO == null || routeDetailsDTO.distance() == null || routeDetailsDTO.geometry() == null) {
                throw new RecalculateEtaException("[markDriverCheckpoint] - dados vindo nulos da API do Mapbox para a viagem: " + travelCached.travelId());
            }

            logger.info("[markDriverCheckpoint] - primeiro cálculo da viagem {} realizado com sucesso. Armazenando no redis.", travelId);

            redisTrackingService.storeCalculatedRouteState(travelId, strLatitude, strLongitude, routeDetailsDTO);
        }
        // faz recalculo da rota/ETA se necessário
        else {
            // verifica se deve recalcular rota
            boolean isShouldRecalculateRoute = shouldRevalidateRoute(latitude, longitude, new RouteCalculationReferenceDTO(routeCalculateReference.lastCalcLat(), routeCalculateReference.lastCalcLng()));

            if (isShouldRecalculateRoute) {
                RouteDeviationDTO routeDeviation = routeCalculationService.isRouteDeviation(new RouteDeviationRequestDTO(travelId, latitude, longitude));

                if (routeState.geometry() == null || routeDeviation.isOffRoute()) {
                    logger.info("[markDriverCheckpoint] - chamado API para recalculo de rota para a viagem: {} ", travelId);
                    RouteDetailsDTO routeDetailsDTO = mapboxAPIService.recalculateETA(longitude, latitude, finalLongitude, finalLatitude);

                    if (routeDetailsDTO == null || routeDetailsDTO.distance() == null || routeDetailsDTO.geometry() == null) {
                        throw new RecalculateEtaException("[markDriverCheckpoint] - dados vindo nulos da API do Mapbox para a viagem: " + travelCached.travelId());
                    }

                    logger.info("[markDriverCheckpoint] - api respondeu com sucesso. Salvando a nova rota calculada para a viagem: {} ", travelId);

                    redisTrackingService.storeCalculatedRouteState(travelId, strLatitude, strLongitude, routeDetailsDTO);
                }
            }
        }

        LiveLocationDTO liveLocationDTO = extractLiveCoordinates(travelId);

        // algoritmo rodando async para verificar status do estudante na viagem - auto disconnect se está muito distante por X tempo
        eventPublisher.publishEvent(new StudentAwayStateCheckEvent(travelId, liveLocationDTO));

        // dispara evento de domínio
        NewLocationReceivedEvents event = new NewLocationReceivedEvents(
                travelId,
                latitude,
                longitude,
                Instant.now(),
                travelCached.travelStatus(),
                speed,
                heading);

        eventPublisher.publishEvent(event);

        eventPublisher.publishEvent(new VehicleGpsMessageDTO(
                cityId.toString(),
                travelId.toString(),
                new VehicleLocationRequestDTO(travelId, latitude, longitude, speed, heading)));
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

        TravelCacheDTO travelStaticCache = travelCacheService.getOrLoadTravelStaticCache(travelId);

        if (travelStaticCache.travelStatus() != TravelStatus.TRAVELLING) {
            throw new TravelException("[processNewLocation] A viagem não está em andamento: " + travelId);
        }

        RouteCalculationReferenceDTO routeCalculateReference = redisTrackingService.getRouteCalculateReference(travelId);
        RouteDetailsDTO routeState = redisTrackingService.getRouteState(travelId);

        if (routeCalculateReference.lastCalcLat() == null || routeCalculateReference.lastCalcLng() == null || routeState == null || routeState.distance() == null) {
            throw new LiveLocationDataNotFoundException("[processNewLocation] Dados obrigatórios do liveLocation são null ou inválidos. Viagem: " + travelId);
        }

        boolean shouldRevalidateRoute = shouldRevalidateRoute(currentLat, currentLng, new RouteCalculationReferenceDTO(routeCalculateReference.lastCalcLat(), routeCalculateReference.lastCalcLng()));

        RouteDetailsDTO currentRouteDetails;
        RouteDeviationDTO routeDeviation = null;

        // valida se precisa recalcular e chama metodo responsavel pelo calculo
        if (shouldRevalidateRoute) {
            routeDeviation = routeCalculationService.isRouteDeviation(new RouteDeviationRequestDTO(travelId, currentLat, currentLng));

            if (routeDeviation.isOffRoute()) {
                currentRouteDetails = calculateEtaFromMapbox(currentLat, currentLng, travelStaticCache.finalLatitude(), travelStaticCache.finalLongitude(), routeState.distance(), routeState.geometry());
            }
            else {
                // precisa recalcular, mas sem desvio de rota
                currentRouteDetails = calculateEtaInternally(travelId, travelStaticCache.distance(), travelStaticCache.polylineRoute());
            }

        } else {
            // sem desvio de rota, realiza cálculo interno
            logger.info("[processNewLocation] - ônibus não se encontra fora de Rota.");

            currentRouteDetails = calculateEtaInternally(travelId, travelStaticCache.distance(), travelStaticCache.polylineRoute());

        }

        // atualiza somente se houve recalculate real de rota
        if (shouldRevalidateRoute && routeDeviation.isOffRoute()) {
            redisTrackingService.storeCalculatedRouteState(
                    travelStaticCache.travelId(),
                    currentLat.toString(),
                    currentLng.toString(),
                    currentRouteDetails);
        }

        redisTrackingService.storeTravelMetadata(
                travelStaticCache.travelId(),
                currentRouteDetails,
                travelStaticCache.travelStatus().toString()
        );
    }

    // haverá um popup no front que perguntará se o estudante irá participar da viagem
    // sem uso por enquanto e ignorado
//    public void confirmEmbarkOnTravel(UUID travelId, UUID studentId) {
//        if (studentId == null || travelId == null) {
//            throw new EmptyMandatoryFieldsFound("[confirmEmbarkOnTravel] parâmetros obrigatórios null ou inválidos para a viagem: " + travelId);
//        }
//
//        StudentTravel studentTravel = studentTravelRepository
//                .findByStudentIdAndTravelId(studentId, travelId)
//                .orElseThrow(() -> new TravelStudentAssociationNotFoundException("[confirmEmbarkOnTravel] Associação travel e student não encontrada. Viagem: " + travelId));
//
//        if (studentTravel.isEmbark()) {
//            throw new BoardingAlreadyConfirmedException("[confirmEmbarkOnTravel] Embarque já confirmado. Viagem: " + travelId);
//        }
//
//        studentTravel.setEmbark(true);
//        studentTravelRepository.save(studentTravel);
//
//        logger.info("[confirmEmbarkOnTravel] embarque do estudante: {} confirmado com sucesso para a viagem: {} ", studentId, travelId);
//    }

    // endpoint de fastview - provê a loc do driver
    public LiveLocationDTO getDriverPosition(UUID travelId) {
        TravelCacheDTO travelStaticCache = travelCacheService.getOrLoadTravelStaticCache(travelId);

        if (travelStaticCache.travelStatus() != TravelStatus.TRAVELLING) {
            throw new TravelException("[getDriverPosition] Viagem " + travelId + " não está em andamento.");
        }

        return extractLiveCoordinates(travelId);
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
        LiveLocationDTO currentLocation = redisTrackingService.getLiveLocation(travelId);

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

    // verifica se deve recalcular
    private boolean shouldRevalidateRoute(Double currentLat, Double currentLng, RouteCalculationReferenceDTO routeCalculationReference) {
        if (currentLat == null || currentLng == null) {
            logger.info("[shouldRevalidateRoute] - currentLat/Lng são null");
            return false;
        }

        if (routeCalculationReference.lastCalcLat() == null || routeCalculationReference.lastCalcLng() == null) {
            logger.info("[shouldRevalidateRoute] - sem referência anterior para os cálculos.");
            return false;
        }

        Double lastCalcLat = routeCalculationReference.lastCalcLat();
        Double lastCalcLng = routeCalculationReference.lastCalcLng();

        Double distanceFromLastCalculation = routeCalculationService.calculateHaversineDistanceInMeters(currentLat, currentLng, lastCalcLat, lastCalcLng);

        if (distanceFromLastCalculation == null) {
            logger.info("[shouldRevalidateRoute] - distância calculada: null");
            return false;
        }

        logger.info("[shouldRevalidateRoute] - distância desde o último cálculo: {} metros", distanceFromLastCalculation);

        return distanceFromLastCalculation > ROUTE_RECALCULATION_THRESHOLD;
    }

    // responsável por realizar o calculo de ETA (rota) com o mapbox p/ onibus fora de rota
    private RouteDetailsDTO calculateEtaFromMapbox(Double currentLatitude, Double currentLongitude, Double travelFinalLatitude, Double travelFinalLongitude, Double routeDistance, String routeGeometry) {
        RouteDetailsDTO newEtaRecalculateByApi;

        // se está fora da rota, chama o mapbox
        newEtaRecalculateByApi = mapboxAPIService.recalculateETA(
                currentLongitude,
                currentLatitude,
                travelFinalLongitude,
                travelFinalLatitude);

        if (newEtaRecalculateByApi == null
                || newEtaRecalculateByApi.duration() == null
                || newEtaRecalculateByApi.distance() == null) {
            throw new RecalculateEtaException("[processNewLocation] resposta inválida da API de rotas");
        }

        return new RouteDetailsDTO(
                newEtaRecalculateByApi.duration(),
                newEtaRecalculateByApi.distance(),
                newEtaRecalculateByApi.geometry());
    }

    // responsavel por calcular o ETA de forma interna,com os dados armazenados no redis p onibus EM ROTA comum
    private RouteDetailsDTO calculateEtaInternally(UUID travelId, Double travelDistance, String polyline) {
        PreviousStateDTO previousEta = redisTrackingService.getPreviousEta(travelId);

        if (previousEta == null || previousEta.timeStamp() == null || previousEta.durationRemaining() == null) {
            throw new EtaDataStatesInvalidException("[processNewLocation] dados do previousEta inválidos ou null para a viagem: " + previousEta);
        }

        long currentTimeMillis = clock.millis();
        long timeElapsedMillis = currentTimeMillis - previousEta.timeStamp();
        double timeElapsedSeconds = (double) timeElapsedMillis / 1000.0;

        double newETARecalculateByInternally = previousEta.durationRemaining() - timeElapsedSeconds;

        // nunca deixa ser valor negativo
        newETARecalculateByInternally = Math.max(0.0, newETARecalculateByInternally);

        return new RouteDetailsDTO(
                newETARecalculateByInternally,
                travelDistance,
                polyline);
    }
}
