package com.travel_system.backend_app.service;

import com.travel_system.backend_app.events.NewLocationReceivedEvents;
import com.travel_system.backend_app.events.StudentAwayStateCheckEvent;
import com.travel_system.backend_app.events.VehicleGpsMessageDTO;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.model.dtos.AnalyzeMovementStateDTO;
import com.travel_system.backend_app.model.dtos.cache.TravelCacheDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.*;
import com.travel_system.backend_app.model.dtos.request.RouteDeviationRequestDTO;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.model.dtos.route.LocationPointDTO;
import com.travel_system.backend_app.model.dtos.route.TravelTrackingSummaryDTO;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.StudentTravelRepository;
import com.travel_system.backend_app.repository.TravelLocationHistoryRepository;
import com.travel_system.backend_app.repository.TravelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;


@Service
public class TravelTrackingService {
    private final Logger log = LoggerFactory.getLogger(TravelTrackingService.class);

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
    private final StudentTravelRouteStopService studentTravelRouteStopService;

    private final Executor routeRecalculationTaskExecutor;

    private final Set<UUID> travelsWithRecalculationInFlight = ConcurrentHashMap.newKeySet();

    private final ApplicationEventPublisher eventPublisher;

    private static final double ROUTE_RECALCULATION_THRESHOLD = 50.0;

    // usar no lugar de Instant.now() para ajudar nos testes unitários
    private final Clock clock;

    public TravelTrackingService(TravelRepository travelRepository, RedisTrackingService redisTrackingService, MapboxAPIService mapboxAPIService, RouteCalculationService routeCalculationService, StudentTravelRepository studentTravelRepository, GpsDataIngestorService gpsDataIngestorService, TravelLocationHistoryRepository travelLocationHistoryRepository, TravelService travelService, LocationService locationService, TravelCacheService travelCacheService, StudentTravelRouteStopService studentTravelRouteStopService, Executor routeRecalculationTaskExecutor, ApplicationEventPublisher eventPublisher, Clock clock) {
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
        this.studentTravelRouteStopService = studentTravelRouteStopService;
        this.routeRecalculationTaskExecutor = routeRecalculationTaskExecutor;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // Anota que o motorista passou pela localização atual e libera o celular o mais rápido possível
    public void markDriverCheckpoint(UUID studentTravelId, UUID travelId, VehicleLocationRequestDTO vehicleLocationRequest) {
        long start = System.currentTimeMillis(); // debugging ttl

        if (!travelId.equals(vehicleLocationRequest.travelId())) {
            throw new IllegalStateException("TravelID da URL diferente do body");
        }

        if (vehicleLocationRequest.latitude() == null || vehicleLocationRequest.longitude() == null || vehicleLocationRequest.speed() == null || vehicleLocationRequest.heading() == null) {
            throw new NoSuchCoordinates("Coordenadas inválidas ou incompletas para o processamento");
        }

        long t1 = System.currentTimeMillis();
        // busca por cache da viagem, caso não haja, faz requisição e armazena os dados em cache para utilizar aqui
        TravelCacheDTO travelCached = travelCacheService.getOrLoadTravelStaticCache(travelId);
        log.info("[TTL] getOrLoadTravelStaticCache: {}ms", System.currentTimeMillis() - t1);


        if (travelCached.travelStatus() != TravelStatus.TRAVELLING) {
            throw new TravelException("A viagem " + travelId + " não está em andamento");
        }

        Double latitude = vehicleLocationRequest.latitude();
        Double longitude = vehicleLocationRequest.longitude();
        Double speed = vehicleLocationRequest.speed();
        Double heading = vehicleLocationRequest.heading();

        // pings real time
        long t2 = System.currentTimeMillis();
        Map<String, String> routeData = redisTrackingService.storeLocationAndReadRouteState(travelId, new CurrentVehicleLocationDTO(latitude, longitude, speed, heading));
        log.info("[TTL] storeLocationAndReadRouteState: {}ms", System.currentTimeMillis() - t2);


        // salva no redis como última posição conhecida matendo a distance e o geometry antigos
        RouteCalculationReferenceDTO routeCalculateReference = redisTrackingService.parseRouteCalculateReference(routeData);
        Optional<RouteDetailsDTO> routeState = redisTrackingService.parseRouteState(routeData);

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

            log.info("[markDriverCheckpoint] - primeiro cálculo da viagem {} realizado com sucesso. Armazenando no redis.", travelId);

            redisTrackingService.storeCalculatedRouteState(travelId, strLatitude, strLongitude, routeDetailsDTO);

            // a partir do resultado do cálculo, atribui o valor das variáveis novamente pq liveLocationDTO é montado localmente com esses valores
            routeState = Optional.of(routeDetailsDTO);
            routeCalculateReference = new RouteCalculationReferenceDTO(latitude, longitude);
        }
        // faz recalculo da rota/ETA se necessário
        else {
            // verifica se deve recalcular rota
            boolean isShouldRecalculateRoute = shouldRevalidateRoute(latitude, longitude, new RouteCalculationReferenceDTO(routeCalculateReference.lastCalcLat(), routeCalculateReference.lastCalcLng()));

            if (isShouldRecalculateRoute) {
                /*
                * para evitar que haja excesso de recalculo caso o ônibus permaneça fora de fora por muito tempo
                * essa key em memória diz que "já tem recálculo em andamento para esta travelId" e apenas pula o recalculo
                * */
                boolean alreadyInFlight  = travelsWithRecalculationInFlight.add(travelId);

                if (alreadyInFlight) {
                    log.info("[markDriverCheckpoint] - recalculo já em andamento para a viagem: {}, ping ignorado para este propósito", travelId);

                } else {
                    log.info("[markDriverCheckpoint] - chamado API para recalculo de rota para a viagem: {} ", travelId);

                    RouteDeviationDTO routeDeviation = routeCalculationService.isRouteDeviation(new RouteDeviationRequestDTO(travelId, latitude, longitude));

                    // valida com base no geometry do redis e no isRouteOff se deve recalcular a rota
                    boolean shouldRecalculate = routeState.map(state -> state.geometry() == null)
                            .orElse(true) || routeDeviation.isOffRoute();

                    if (shouldRecalculate) {

                        routeRecalculationTaskExecutor.execute(() -> {
                            try {
                                RouteDetailsDTO routeDetailsDTO = mapboxAPIService.recalculateETA(longitude, latitude, finalLongitude, finalLatitude);

                                if (routeDetailsDTO == null || routeDetailsDTO.distance() == null || routeDetailsDTO.geometry() == null) {
                                    throw new RecalculateEtaException("[markDriverCheckpoint] - dados vindo nulos da API do Mapbox para a viagem: " + travelCached.travelId());
                                }

                                redisTrackingService.storeCalculatedRouteState(travelId, strLatitude, strLongitude, routeDetailsDTO);
                            } catch (Exception e) {
                                log.error("[markDriverCheckpoint] - falha ao recalcular rota assíncrona para viagem: {}", travelId, e);
                            } finally {
                                // libera a guarda independente de sucesso ou falha, evitando que a viagem fique travada
                                travelsWithRecalculationInFlight.remove(travelId);
                            }
                        });
                    }
                }
            }
        }

        Instant pingTimestamp = Instant.now();

        LiveLocationDTO liveLocationDTO = new LiveLocationDTO(
                latitude,
                longitude,
                routeState.map(RouteDetailsDTO::geometry).orElse(null),
                routeState.map(RouteDetailsDTO::distance).orElse(null),
                routeCalculateReference != null ? routeCalculateReference.lastCalcLat() : null,
                routeCalculateReference != null ? routeCalculateReference.lastCalcLng() : null,
                pingTimestamp
        );

        long t3 = System.currentTimeMillis();
        // algoritmo rodando async para verificar status do estudante na viagem - auto disconnect se está muito distante por X tempo
        eventPublisher.publishEvent(new StudentAwayStateCheckEvent(travelId, liveLocationDTO));

        // dispara evento de domínio
        NewLocationReceivedEvents event = new NewLocationReceivedEvents(
                travelId,
                latitude,
                longitude,
                pingTimestamp,
                travelCached.travelStatus(),
                speed,
                heading);

        eventPublisher.publishEvent(event);

        eventPublisher.publishEvent(new VehicleGpsMessageDTO(
                travelCached.cityId().toString(),
                travelId.toString(),
                new VehicleLocationRequestDTO(travelId, latitude, longitude, speed, heading)));
        log.info("[TTL] 3 publishEvent: {}ms", System.currentTimeMillis() - t3);

        long t4 = System.currentTimeMillis();
        // evento de processamento da aproximação do veículo ao ponto de parada do estudante
        studentTravelRouteStopService.processRouteStopApproach(travelId, studentTravelId);
        log.info("[TTL] processRouteStopApproach: {}ms", System.currentTimeMillis() - t4);


        long elapsed = System.currentTimeMillis() - start;
        log.info("[markDriverCheckpoint] tempo para executar o mark-driver-checkpoint: {}", elapsed);
    }

    // Orquestra o sistema de tracking em tempo real, verificando desvios de rota,
    // recalculando o ETA e salvando a localização e os metadados da viagem no Redis
    public void processNewLocation(VehicleLocationRequestDTO vehicleLocationRequest) {
        if (vehicleLocationRequest == null || vehicleLocationRequest.travelId() == null || vehicleLocationRequest.latitude() == null || vehicleLocationRequest.longitude() == null) {
            throw new EmptyMandatoryFieldsFoundException("[processNewLocation] campos de entrada obrigatórios null ou inválidos: " + vehicleLocationRequest);
        }

        UUID travelId = vehicleLocationRequest.travelId();
        Double currentLat = vehicleLocationRequest.latitude();
        Double currentLng = vehicleLocationRequest.longitude();

        TravelCacheDTO travelStaticCache = travelCacheService.getOrLoadTravelStaticCache(travelId);

        if (travelStaticCache.travelStatus() != TravelStatus.TRAVELLING) {
            throw new TravelException("[processNewLocation] A viagem não está em andamento: " + travelId);
        }

        RouteCalculationReferenceDTO routeCalculateReference = redisTrackingService.getRouteCalculateReference(travelId);
        Optional<RouteDetailsDTO> routeStateOpt = redisTrackingService.getRouteState(travelId);

        boolean missingRequiredRouteData = routeStateOpt.map(state -> state.geometry() == null || state.distance() == null).orElse(true);

        if (routeCalculateReference == null || routeCalculateReference.lastCalcLat() == null || routeCalculateReference.lastCalcLng() == null || missingRequiredRouteData) {
            throw new LiveLocationDataNotFoundException("[processNewLocation] Dados obrigatórios do liveLocation são null ou inválidos. Viagem: " + travelId);
        }

        RouteDetailsDTO validRouteState = routeStateOpt.get();

        boolean shouldRevalidateRoute = shouldRevalidateRoute(currentLat, currentLng, new RouteCalculationReferenceDTO(routeCalculateReference.lastCalcLat(), routeCalculateReference.lastCalcLng()));

        RouteDetailsDTO currentRouteDetails;
        RouteDeviationDTO routeDeviation = null;

        // valida se precisa recalcular e chama metodo responsavel pelo calculo
        if (shouldRevalidateRoute) {
            routeDeviation = routeCalculationService.isRouteDeviation(new RouteDeviationRequestDTO(travelId, currentLat, currentLng));

            if (routeDeviation.isOffRoute()) {
                currentRouteDetails = calculateEtaFromMapbox(currentLat, currentLng, travelStaticCache.finalLatitude(), travelStaticCache.finalLongitude(), validRouteState.distance(), validRouteState.geometry());
            }
            else {
                // precisa recalcular, mas sem desvio de rota
                currentRouteDetails = calculateEtaInternally(travelId, travelStaticCache.distance(), travelStaticCache.polylineRoute());
            }

        } else {
            // sem desvio de rota, realiza cálculo interno
            log.info("[processNewLocation] - ônibus não se encontra fora de Rota.");

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

    // endpoint de fastview - provê a loc do driver
    public TravelTrackingSummaryDTO getDriverPosition(UUID travelId) {
        TravelCacheDTO travelStaticCache = travelCacheService.getOrLoadTravelStaticCache(travelId);

        if (travelStaticCache.travelStatus() != TravelStatus.TRAVELLING) {
            throw new TravelException("[getDriverPosition] Viagem " + travelId + " não está em andamento.");
        }

        LiveLocationDTO liveLocationDTO = extractLiveCoordinates(travelId);

        // retorna o último ETA armazenado + a distância
        PreviousStateDTO previousEta = redisTrackingService.getPreviousEta(travelId);
        // fornece o último estado do veículo
        AnalyzeMovementStateDTO lastMovementState = redisTrackingService.getLastMovementState(travelId);

        Double durationRemaining = previousEta != null ? previousEta.durationRemaining() : null;
        Double distanceRemaining = previousEta != null ? previousEta.distanceRemaining() : null;
        String movementState = lastMovementState != null ? lastMovementState.movementState().name() : "UNKNOWN";
        String travelStatus = travelStaticCache.travelStatus().name();

        return new TravelTrackingSummaryDTO(
                liveLocationDTO.latitude(),
                liveLocationDTO.longitude(),
                liveLocationDTO.geometry(),
                distanceRemaining,
                durationRemaining,
                movementState,
                travelStatus,
                liveLocationDTO.lastCalcLat(),
                liveLocationDTO.lastCalcLng(),
                liveLocationDTO.current_location_timestamp()
        );
    }

    // fornece um histórico de points salvos no banco
    public Page<LocationPointDTO> getTravelHistory(UUID travelId) {
        if (travelId == null) {
            throw new EmptyMandatoryFieldsFoundException("[getTravelHistory] Dados de parâmetros inválidos ou não encontrados");
        }

        Pageable pageable = PageRequest.of(0, 100);

        return travelLocationHistoryRepository.findLatLongByTravelIdAsc(travelId, pageable);
    }

    // MÉTODOS AUXILIARES
    private LiveLocationDTO extractLiveCoordinates(UUID travelId) {
        LiveLocationDTO currentLocation = redisTrackingService.getLiveLocation(travelId);

        log.info("currentLocation: {}", currentLocation);

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
                currentLocation.lastCalcLng(),
                currentLocation.current_location_timestamp()
        );

    }

    // verifica se deve recalcular
    private boolean shouldRevalidateRoute(Double currentLat, Double currentLng, RouteCalculationReferenceDTO routeCalculationReference) {
        if (currentLat == null || currentLng == null) {
            log.info("[shouldRevalidateRoute] - currentLat/Lng são null");
            return false;
        }

        if (routeCalculationReference.lastCalcLat() == null || routeCalculationReference.lastCalcLng() == null) {
            log.info("[shouldRevalidateRoute] - sem referência anterior para os cálculos.");
            return false;
        }

        Double lastCalcLat = routeCalculationReference.lastCalcLat();
        Double lastCalcLng = routeCalculationReference.lastCalcLng();

        Double distanceFromLastCalculation = routeCalculationService.calculateHaversineDistanceInMeters(currentLat, currentLng, lastCalcLat, lastCalcLng);

        if (distanceFromLastCalculation == null) {
            log.info("[shouldRevalidateRoute] - distância calculada: null");
            return false;
        }

        log.info("[shouldRevalidateRoute] - distância desde o último cálculo: {} metros", distanceFromLastCalculation);

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
