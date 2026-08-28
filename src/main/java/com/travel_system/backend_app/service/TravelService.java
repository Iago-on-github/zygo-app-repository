package com.travel_system.backend_app.service;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.interfaces.mappers.RouteStopResponseMapper;
import com.travel_system.backend_app.interfaces.mappers.StandardRouteResponseMapper;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.StudentTrackingPositionDTO;
import com.travel_system.backend_app.model.dtos.TravelPreviewDTO;
import com.travel_system.backend_app.model.dtos.cache.StudentTravelCacheDTO;
import com.travel_system.backend_app.model.dtos.cache.StudentTravelRouteStopTrackingCacheDTO;
import com.travel_system.backend_app.model.dtos.cache.TravelCacheDTO;
import com.travel_system.backend_app.model.dtos.request.TravelRequestDTO;
import com.travel_system.backend_app.model.dtos.response.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.enums.*;
import com.travel_system.backend_app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.travel_system.backend_app.config.constants.GlobalAppConstants.MONITORING_THRESHOLD;

@Service
public class TravelService {

    private final TravelRepository travelRepository;
    private final StudentTravelRepository studentTravelRepository;
    private final StudentRepository studentRepository;
    private final DriverRepository driverRepository;
    private final TravelReportsRepository travelReportsRepository;
    private final TravelLocationHistoryRepository travelLocationHistoryRepository;
    private final StandardRouteRepository standardRouteRepository;

    private final MapboxAPIService mapboxAPIService;
    private final RedisTrackingService redisTrackingService;
    private final PolylineService polylineService;
    private final TravelCacheService travelCacheService;
    private final TravelStudentStateCacheService travelStudentStateCacheService;
    private final TravelNotificationService travelNotificationService;
    private final StudentTravelRouteStopService studentTravelRouteStopService;
    private final TravelTrackingStaticCacheService travelTrackingStaticCacheService;

    private final RouteStopResponseMapper routeStopResponseMapper;
    private final StandardRouteResponseMapper standardRouteResponseMapper;

    private final Logger log = LoggerFactory.getLogger(TravelService.class);

    public TravelService(TravelRepository travelRepository, StudentTravelRepository studentTravelRepository, StudentRepository studentRepository, DriverRepository driverRepository, StandardRouteRepository standardRouteRepository, MapboxAPIService mapboxAPIService, RedisTrackingService redisTrackingService, TravelReportsRepository travelReportsRepository, TravelLocationHistoryRepository travelLocationHistoryRepository, PolylineService polylineService, TravelCacheService travelCacheService, TravelStudentStateCacheService travelStudentStateCacheService, TravelNotificationService travelNotificationService, StudentTravelRouteStopService studentTravelRouteStopService, TravelTrackingStaticCacheService travelTrackingStaticCacheService, RouteStopResponseMapper routeStopResponseMapper, StandardRouteResponseMapper standardRouteResponseMapper) {
        this.travelRepository = travelRepository;
        this.studentTravelRepository = studentTravelRepository;
        this.studentRepository = studentRepository;
        this.driverRepository = driverRepository;
        this.standardRouteRepository = standardRouteRepository;
        this.mapboxAPIService = mapboxAPIService;
        this.redisTrackingService = redisTrackingService;
        this.travelReportsRepository = travelReportsRepository;
        this.travelLocationHistoryRepository = travelLocationHistoryRepository;
        this.polylineService = polylineService;
        this.travelCacheService = travelCacheService;
        this.travelStudentStateCacheService = travelStudentStateCacheService;
        this.travelNotificationService = travelNotificationService;
        this.studentTravelRouteStopService = studentTravelRouteStopService;
        this.travelTrackingStaticCacheService = travelTrackingStaticCacheService;
        this.routeStopResponseMapper = routeStopResponseMapper;
        this.standardRouteResponseMapper = standardRouteResponseMapper;
    }

    @Transactional
    public TravelResponseDTO createTravel(TravelRequestDTO travelRequestDTO) {
        Travel travel = new Travel();

        Driver driver = driverRepository.findById(travelRequestDTO.driverId())
                .orElseThrow(EntityNotFoundException::new);

        if (driver.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new InactiveDriverException("Motorista inativo, não é possível prosseguir. driverId: " + driver.getId());
        }

        boolean hasActiveTravel = travelRepository.existsByDriverIdAndTravelStatusIn(driver.getId(), List.of(TravelStatus.PENDING, TravelStatus.TRAVELLING));

        if (hasActiveTravel) {
            throw new TravelException("Motorista já possui uma viagem em andamento, não é possível prosseguir: " + driver.getId());
        }

        // customer da viagem é herdado diretamente do driver
        travel.setCustomerId(driver.getCustomerId());

        travel.setOriginLongitude(travelRequestDTO.originLongitude());
        travel.setOriginLatitude(travelRequestDTO.originLatitude());
        travel.setFinalLongitude(travelRequestDTO.finalLongitude());
        travel.setFinalLatitude(travelRequestDTO.finalLatitude());

        if (travelRequestDTO.travelPeriod() == null) {
            throw new TravelException("O período da viagem precisa ser selecionado.");
        }

        travel.setTravelPeriod(travelRequestDTO.travelPeriod());

        travel.setCreatedAt(Instant.now());
        travel.setTravelStatus(TravelStatus.PENDING);
        travel.setDriver(driver);

        /*
        * verificação da rota padrão da viagem
        * */

        StandardRoute standardRoute = standardRouteRepository.findById(travelRequestDTO.standardRouteId())
                .orElseThrow(() -> new EntityNotFoundException("Rota Padrão não encontrada"));

        if (standardRoute.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new StandardRouteException("A Rota Padão está INATIVA no sistema");
        }

        // verifica compatibilidade entre Customers
        if (!isSameCustomer(travel.getCustomerId(), standardRoute.getCustomerId())) {
            throwTravelException("A Rota Padrão deve obrigariamente ser do mesmo customer da Viagem");
        }

        travel.setStandardRoute(standardRoute);

        // obtém preview da viagem
        TravelPreviewDTO tripPreview = mapboxAPIService.getTripPreview(
                travelRequestDTO.originLongitude(),
                travelRequestDTO.originLatitude(),
                travelRequestDTO.finalLongitude(),
                travelRequestDTO.finalLatitude());

        if (tripPreview == null || tripPreview.distance() == null || tripPreview.duration() == null) {
            throw new RecalculateEtaException("Falha ao buscar dados de Preview da API");
        }

        // armazena dados 'preview' da viagem
        travel.setDistance(tripPreview.distance());
        travel.setDuration(tripPreview.duration());

        travel.setDestinationCity(travelRequestDTO.destinationCity());

        travelRepository.save(travel);

        // envia notificação para o firebase comunicando a criação da viagem
        travelNotificationService.sendTravelCreatedNotification(travel);

        return travelConverted(travel);
    }

    @Transactional
    public void startTravel(UUID travelId) {
        Travel actualTrip = travelRepository.findById(travelId)
                .orElseThrow(() -> new TripNotFound("Viagem não encontrada: " + travelId));

        if (actualTrip.getTravelStatus() == TravelStatus.FINISH ||
                actualTrip.getTravelStatus() == TravelStatus.TRAVELLING ||
                actualTrip.getTravelStatus() == TravelStatus.CANCELED) {
            throwTravelException("Não é possível iniciar a viagem " + travelId + " por conta do status: " + actualTrip.getTravelStatus());
        }

        // chama o mapboxservice para calcular a rota
        RouteDetailsDTO routeDetailsDTO = mapboxAPIService.calculateRoute(
                actualTrip.getOriginLongitude(),
                actualTrip.getOriginLatitude(),
                actualTrip.getFinalLongitude(),
                actualTrip.getFinalLatitude(),
                List.of());

        if (routeDetailsDTO == null ||
                routeDetailsDTO.duration() == null ||
                routeDetailsDTO.distance() == null ||
                routeDetailsDTO.geometry() == null) {
            throw new RecalculateEtaException("Falha ao calcular rota: API não retornou dados válidos para a viagem: " + travelId);
        }

        // preenche os dados estáticos com o routesDetailsDto
        actualTrip.setDuration(routeDetailsDTO.duration());
        actualTrip.setDistance(routeDetailsDTO.distance());
        actualTrip.setPolylineRoute(routeDetailsDTO.geometry());
        actualTrip.setStartHourTravel(Instant.now());

        actualTrip.setTravelStatus(TravelStatus.TRAVELLING);

        travelRepository.save(actualTrip);

        // envia notificação para o firebase comunicando o incio da viagem
        travelNotificationService.sendTravelStartedNotification(actualTrip);

        // adiciona viagem ativa ao redis para métricas de self-health do sistema
        redisTrackingService.addActiveTravel(travelId);

        // limpa o cache estático da viagem (por ter mudado o STATUS da viagem)
        travelCacheService.invalidateTravelStaticCache(travelId);

        log.info("viagem {} iniciada com sucesso. ", travelId);
    }

    @Transactional
    public void endTravel(UUID travelId) {
        Travel actualTrip = travelRepository.findById(travelId)
                .orElseThrow(() -> new TripNotFound("Viagem não encontrada: " + travelId));

        if (!(actualTrip.getTravelStatus() == TravelStatus.TRAVELLING)) {
            throwTravelException("Não é possível prosseguir, a viagem não está em andamento: " + travelId);
        }

        actualTrip.setTravelStatus(TravelStatus.FINISH);
        actualTrip.setEndHourTravel(Instant.now());

        int totalStudentsCount = actualTrip.getStudentTravels().size();
        long embarkedStudentsCount = actualTrip.getStudentTravels().stream()
                .filter(student -> student.getEmbarkHour() != null && student.isEmbark()).count();

        long percentual = 0;
        if (totalStudentsCount != 0 && embarkedStudentsCount != 0) {
            percentual = embarkedStudentsCount * 100 / totalStudentsCount;
        }

        UUID baseCustomerId = actualTrip.getCustomerId();

        // realiza o desvínculo de estudantes
        actualTrip.getStudentTravels().forEach(studentTravel -> {
            UUID studentsCustomerId = studentTravel.getStudent().getCustomerId();

            if (studentTravel.isEmbark() && isSameCustomer(baseCustomerId, studentsCustomerId)) {
                studentTravel.setEmbark(false);
                studentTravel.setDisembarkHour(Instant.now());
                studentTravelRepository.save(studentTravel);

                // limpa o redis para o contexto do algoritmo de proximidade do routestop
                redisTrackingService.deleteStudentTravelRouteStopMonitoring(travelId, studentTravel.getId());
            }

            // limpa o cache estático do tracking da viagem p/ o estudante
            travelTrackingStaticCacheService.removeStudentTravelTrackingCache(travelId, studentTravel.getId());

            // limpa o redis para o contexto do algoritmo de proximidade do routestop
            redisTrackingService.deleteStudentTravelRouteStopMonitoring(travelId, studentTravel.getId());

            log.info("[endTravel] estudantes desvinculados da viagem: {} ", studentTravel.getId());
        });

        // obtem os dados de lat/lng para formar a polyline da viagem
        List<TravelLocationHistory> travelRecorded = travelLocationHistoryRepository
                .findAllByTravelIdOrderByTimestampAsc(travelId);

        List<Point> pointList = travelRecorded.stream()
                .filter(t -> t.getLatitude() != null && t.getLongitude() != null)
                // atentar-se que, no Point, a LONGITUDE sempre será primeiro
                .map(t -> Point.fromLngLat(t.getLongitude(), t.getLatitude())).toList();

        String polylineEncoded = polylineService.formattedPolylineEncoded(pointList);

        // polyline, em cenários sem falha interna, pode retornar null caso a viagem seja encerrada muito cedo
        if (polylineEncoded == null || polylineEncoded.isBlank()) {
            log.warn("[endTravel]: polyline retornando null, salvando string vazia. Viagem: {}", travelId );
        }

        // COLETA  DE MÉTRICAS SOBRE A VIAGEM
        Double accumulatedDistance = Double.valueOf(redisTrackingService.getAccumulatedDistance(travelId));
        Duration durationInMinutes = Duration.between(actualTrip.getStartHourTravel(), actualTrip.getEndHourTravel());
        double formattedDurationInMinutes = (double) durationInMinutes.toMinutes() / 60.0;

        TravelReports travelReports = new TravelReports(
                actualTrip,
                accumulatedDistance,
                formattedDurationInMinutes,
                polylineEncoded,
                Instant.now(),
                totalStudentsCount, // expectativa de estudantes na viagem
                (int) embarkedStudentsCount, // ocupação total de estudantes embarcados
                (int) percentual);

        travelReportsRepository.save(travelReports);

        // envia notificação para o firebase comunicando o fim da viagem
        travelNotificationService.sendTravelEndedNotification(actualTrip);

        // deleta os polylines para evitar lixo no banco
        // obs.: passível de usar tarefas agendadas para apagar somente dps de um determinado tempo
        travelLocationHistoryRepository.deleteAllByTravelId(travelId);

        travelRepository.save(actualTrip);

        // adiciona +1 no número de totaltrips do motorista
        setCountDriverTrips(actualTrip);

        redisTrackingService.clearTravelLocationCache(travelId);

        // limpa o cache estático da viagem (por ter mudado o STATUS da viagem)
        travelCacheService.invalidateTravelStaticCache(travelId);

        log.info("Viagem: {} encerrada com sucesso", travelId);
    }

    @Transactional
    public void joinTravel(UUID travelId, String studentEmail, StudentTravelStatus status) {
        if (travelId == null || studentEmail == null || status == null) {
            throw new IllegalArgumentException("[joinTravel] travelId " + travelId +  " ou studentEmail "+ studentEmail + " ou status vindo nulos");
        }

        boolean isAlreadyActive = studentTravelRepository.existsByTravelIdAndStudentEmailAndEmbarkTrue(travelId, studentEmail);

        // verfica se o estudante já está embarcado em outra viagem, ignorando a viagem atual emq ele quer embarcar
        boolean isAlreadyInAnotherTrip = studentTravelRepository.existsByStudentEmailAndEmbarkTrue(studentEmail, TravelStatus.TRAVELLING, travelId);

        if (isAlreadyInAnotherTrip) {
            throw new TravelException("Estudante " + studentEmail + " está vinculado em outra viagem no momento");
        }

        if (isAlreadyActive) {
            throw new StudentAlreadyLinkedToTrip("Estudante " + studentEmail + " já vinculado à viagem:" + travelId);
        }

        Travel trip = travelRepository.getReferenceById(travelId);

        if (trip.getTravelStatus() != TravelStatus.TRAVELLING) {
            throw new TravelException("Viagem " + travelId + " não está em andamento.");
        }

        Student student = studentRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new EntityNotFoundException("Estudante com email " + studentEmail + " não encontrado"));

        UUID baseCustomerId = trip.getCustomerId();
        UUID studentsCustomerId = student.getCustomerId();

        if (!isSameCustomer(baseCustomerId, studentsCustomerId)) {
            throwTravelException("O estudante deve obrigariamente ser do mesmo customer");
        }

        persistStudentLink(trip, student, status);
    }

    @Transactional
    public void driverChanged(UUID travelId, UUID driverId) {
        Travel actualTrip = travelRepository.findById(travelId)
                .orElseThrow(() -> new TripNotFound("Viagem não encontrada: " + travelId));

        if (actualTrip.getTravelStatus() == TravelStatus.CANCELED || actualTrip.getTravelStatus() == TravelStatus.FINISH) {
            throwTravelException("Não é possível alterar o motorista de uma viagem cancelada ou finalizada");
        }

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new EntityNotFoundException("Motorista " + driverId + " não encontrado."));

        if (driver.getStatus().equals(GeneralStatus.INACTIVE)) {
            throw new InactiveDriverException("Motorista inativo, não é possível prosseguir. driverId: " + driver.getId());
        }

        boolean hasActiveTravel = travelRepository.existsByDriverIdAndTravelStatusIn(driver.getId(), List.of(TravelStatus.PENDING, TravelStatus.TRAVELLING));

        if (hasActiveTravel) {
            throwTravelException("Motorista já possui uma viagem em andamento, não é possível prosseguir: " + driver.getId());
        }

        UUID actuallyDriverCustomerId = actualTrip.getDriver().getCustomerId();
        UUID driverCandidateCustomerId = driver.getCustomerId();

        if (!isSameCustomer(actuallyDriverCustomerId, driverCandidateCustomerId)) {
            throw new CustomerMismatchException("Motoristas devem pertencer ao mesmo Customer");
        }

        actualTrip.setDriver(driver);

        travelRepository.save(actualTrip);

        // envia notificação para o firebase comunicando o cancelamento da viagem
        travelNotificationService.sendDriverChangedNotification(actualTrip, driver);
    }

    @Transactional
    public void cancelTravel(UUID travelId) {
        Travel actualTrip = travelRepository.findById(travelId)
                .orElseThrow(() -> new TripNotFound("Viagem não encontrada: " + travelId));

        if (actualTrip.getTravelStatus() != TravelStatus.PENDING) {
            throwTravelException("Não é possível prosseguir, a viagem " + travelId + " já foi finalizada ou esté em andamento");
        }

        actualTrip.setTravelStatus(TravelStatus.CANCELED);
        actualTrip.setEndHourTravel(Instant.now());

        UUID baseCustomerId = actualTrip.getCustomerId();

        // verifica se existem estudantes vinculados e faz a deconexão
        if (!actualTrip.getStudentTravels().isEmpty()) {
            actualTrip.getStudentTravels().forEach(studentTravel -> {
                UUID studentsCustomerId = studentTravel.getStudent().getCustomerId();

                if (studentTravel.isEmbark() && isSameCustomer(baseCustomerId, studentsCustomerId)) {
                    studentTravel.setEmbark(false);
                    studentTravel.setDisembarkHour(Instant.now());
                    studentTravelRepository.save(studentTravel);
                }

                // evento route_stop_algorithm viagem cancelada
                studentTravelRouteStopService.cancelledStudentRouteStop(travelId, studentTravel.getId(), baseCustomerId);

                log.info("[cancelTravel] estudantes desvinculados da viagem: {} ", studentTravel.getId());
            });
        }

        travelRepository.save(actualTrip);

        // envia notificação para o firebase comunicando o cancelamento da viagem
        travelNotificationService.sendTravelCanceledNotification(actualTrip);

        // não deve registrar nenhum tipo de histórico de viagem
    }

    @Transactional
    public void leaveTravel(UUID travelId, String studentEmail, StudentTravelStatus studentTravelStatus) {
        if (travelId == null || studentEmail == null || studentTravelStatus == null) {
            throw new IllegalArgumentException("[leaveTravel] travelId " + travelId +  " ou studentEmail "+ studentEmail + " vindo nulos");
        }

        // recupera dados das viagens via cache, perante estratégia "getOrLoad"
        TravelCacheDTO travelStaticCache = travelCacheService.getOrLoadTravelStaticCache(travelId);
        StudentTravelCacheDTO studentTravelCache = travelStudentStateCacheService.getOrLoadStudentTravelCache(travelId, studentEmail);

        if (travelStaticCache.travelStatus() != TravelStatus.TRAVELLING) {
            throw new TravelException("Viagem " + travelId + " não está em andamento.");
        }

        // verifica se o estudante NÃO ESTÁ ativo na viagem
        if (studentTravelCache.studentId() == null || !studentTravelCache.embark()) {
            throw new TravelStudentAssociationNotFoundException("Estudante " + studentEmail + " não está ATIVO na viagem.");
        }

        deactivateStudentLink(travelId, studentTravelCache, studentTravelStatus);
    }

    // responsável por obter apenas a viagem onde o estudante está atualmente embarcado
    public ActiveStudentTravelDTO getActiveTravelByStudent(String studentEmail) {
        // retorna os dados com base na viagem que o estudante está vinculado

        return studentTravelRepository
                .findActiveTravelByStudentTravelId(studentEmail, StudentTravelStatus.ACTIVE, TravelStatus.TRAVELLING)
                .orElseThrow(() -> new StudentNotLinkedToTripException("Estudante " + studentEmail + " não está ativo em nenhuma viagem"));
    }

    public Set<StudentTrackingPositionDTO> linkedStudentTravel(UUID travelId) {
        long start = System.currentTimeMillis(); // debbuging

        Set<StudentTrackingPositionDTO> linkedStudents = travelRepository.findTrackingPositionsByTravelId(travelId);

        if (linkedStudents.isEmpty()) {
            log.debug("[linkedStudentTravel] Nenhum estudante vinculado à viagem {}", travelId);
            return Collections.emptySet();
//            throw new StudentNotLinkedToTripException("Nenhum estudante vincualado à viagem: " + travelId);
        }

        long executingTime = System.currentTimeMillis() - start;
        log.info("[linkedStudentTravel] - método que busca a viagem com estudantes. Tempo de execução: {} ", executingTime);

        return linkedStudents;
    }

    // recupera a rota padrão da viagem
    public StandardRouteResponseDTO getTravelStandardRoute(UUID travelId) {
        StandardRoute standardRouteByTravel = travelRepository.findStandardRouteByTravelId(travelId);

        if (standardRouteByTravel == null) {
            throw new TravelException("Rota Padrão não encontrada para a viagem: " + travelId);
        }

        return standardRouteResponseMapper.toDTO(standardRouteByTravel);
    }

    @Cacheable(value = "studentLogged", key = "#studentId + '-' + #travelId")
    public boolean isStudentLogged(UUID studentId, UUID travelId) {
            return studentTravelRepository.existsByIdAndTravelId(studentId, travelId);
    }

    @Cacheable(value = "driverLogged", key = "#userId + '-' + #travelId")
    public boolean isDriverLogged(String userId, UUID travelId) {
        // o "user" é o UUID do usuário logado
        try {
            UUID driverId = UUID.fromString(userId);

            return travelRepository.existsByIdAndDriverId(travelId, driverId);
        } catch (IllegalArgumentException e) {
            log.debug("[isDriverLogged] bateu na exception e retornou false");
            return false;
        }
    }

    public TravelPreviewDTO getTravelPreview(UUID travelId) {
        Travel travel = travelRepository.findById(travelId)
                .orElseThrow(() -> new EntityNotFoundException("Viagem " + travelId + " não encontrada"));

        String arrivalTime = null;

        // faz o cálculo do arrivalTime baseando-se na hora de inicio da viagem
        if (travel.getStartHourTravel() != null && travel.getDuration() != null) {
            arrivalTime = travel.getStartHourTravel().plusSeconds(travel.getDuration().longValue()).toString();
        }

        return new TravelPreviewDTO(travel.getDistance(), travel.getDuration(), travel.getDestinationCity(), arrivalTime);
    }

    // MÉTODOS AUXILIARES
    // MÉTODOS AUXILIARES
    // MÉTODOS AUXILIARES

    private boolean isSameCustomer(UUID baseCustomerId, UUID customerId) {
        return baseCustomerId.equals(customerId);
    }

    private StudentTravelResponseDTO studentTravelMapper(StudentTravel studentTravel) {
        return new StudentTravelResponseDTO(
                studentTravel.getId(),
                studentTravel.getTravel().getId(),
                studentTravel.getStudent().getId(),
                studentTravel.getEmbarkHour(),
                studentTravel.getDisembarkHour(),
                studentTravel.getPosition());
    }

    private void persistStudentLink(Travel travel, Student student, StudentTravelStatus status) {
        StudentTravel studentTravel = new StudentTravel();

        studentTravel.setTravel(travel);
        studentTravel.setStudent(student);
        studentTravel.setEmbark(true);
        studentTravel.setEmbarkHour(Instant.now());
        studentTravel.setStudentTravelStatus(status);

        studentTravelRepository.save(studentTravel);

        // carrega os dados iniciais da viagem para o cache assim que a viagem é iniciada
        loadTravelDataToCache(travel, student, studentTravel);

        boolean routeStopCompatible = isRouteStopCompatible(travel, studentTravel);

        /*
        * verifica se o estudante tem pontos de parada vinculados à viagem,
        * se não tiver chama método p/ publicar evento de incompatibilidade de rota + lançamento de notificação
        * */
        if (!routeStopCompatible) {
            // evento de incompatibilidade
            UUID studentId = studentTravel.getStudent().getId();
            UUID customerId = travel.getCustomerId();

            studentTravelRouteStopService.validateStudentTravelRouteStop(travel.getId(), studentTravel.getId(), studentId, customerId);
        } else {
            // inicializa o monitoriamento do ponto de parada para o estudante
            studentTravelRouteStopService.initializeStudentTravelRouteStopTracking(travel.getId(), studentTravel.getId());
        }

        travelStudentStateCacheService.evictStudentTravelCachedData(travel.getId(), student.getEmail());

        // armazena métrica de salvamento em cache

    }

    private void deactivateStudentLink(UUID travelId, StudentTravelCacheDTO studentTravelCache, StudentTravelStatus studentTravelStatus) {
        long start = System.currentTimeMillis(); // debugging ttl

        String studentEmail = studentTravelCache.studentEmail();

        UUID studentTravelId = studentTravelCache.studentTravelId();
        Instant disembarkHour = Instant.now();

        // faz a persistencia, validando o desvinculo
        studentTravelRepository.disconnectedStudentFromTrip(List.of(studentTravelId), studentTravelStatus, disembarkHour, false);

        // remove as respectivas keys do redis para o aluno em específico
        travelStudentStateCacheService.evictStudentTravelCachedData(travelId, studentEmail);

        // limpa o cache estático do tracking da viagem p/ o estudante
        travelTrackingStaticCacheService.removeStudentTravelTrackingCache(travelId, studentTravelId);

        // limpa o redis para o contexto do algoritmo de proximidade do routestop
        redisTrackingService.deleteStudentTravelRouteStopMonitoring(travelId, studentTravelId);

        long elapsed = System.currentTimeMillis() - start;
        log.info("[leaveTravel] tempo para executar o leave-travel: {}", elapsed);
    }

    private void throwTravelException(String msg) {
        throw new TravelException(msg);
    }

    private TravelResponseDTO travelConverted(Travel travel) {
        DriverResponseDTO driverResponseDTO = driverMapper(travel.getDriver());
        StandardRouteSimpleResponseDTO standardRouteSimpleResponseDTO = standardRouteSimpleMapper(travel.getStandardRoute());

        TravelPreviewDTO travelPreviewDTO = getTravelPreviewDTO(travel);

        return new TravelResponseDTO(
                travel.getId(),
                travel.getTravelStatus(),
                travel.getTravelPeriod(),
                driverResponseDTO,
                standardRouteSimpleResponseDTO,
                travel.getStudentTravels(),
                travel.getCreatedAt(),
                travel.getStartHourTravel(),
                travelPreviewDTO
        );
    }

    private static TravelPreviewDTO getTravelPreviewDTO(Travel travel) {
        String arrivalTime = null;

        // Usa campo "createdAt" para exibir preview ao motorista APENAS ao criar a viagem (sem inicia-la)
        if (travel.getCreatedAt() != null && travel.getDuration() != null) {
            arrivalTime = travel.getCreatedAt()
                    .plusSeconds(travel.getDuration().longValue()).toString();
        }

        return new TravelPreviewDTO(travel.getDistance(), travel.getDuration(), travel.getDestinationCity(), arrivalTime);
    }

    private DriverResponseDTO driverMapper(Driver driver) {

        return new DriverResponseDTO(
                driver.getId(),
                driver.getName(),
                driver.getLastName(),
                driver.getEmail(),
                driver.getTelephone(),
                driver.getProfilePicture(),
                driver.getCreatedAt(),
                driver.getStatus(),
                driver.getAreaOfActivity(),
                driver.getTotalTrips(),
                driver.getCustomerId()
        );
    }

    private StandardRouteSimpleResponseDTO standardRouteSimpleMapper(StandardRoute standardRoute) {
        return new StandardRouteSimpleResponseDTO(
                standardRoute.getId(),
                standardRoute.getRouteName(),
                standardRoute.getRouteDescription(),
                standardRoute.getTravelPeriods(),
                standardRoute.getStatus()
                );
    }

    private void setCountDriverTrips(Travel travel) {
        Integer totalTrips = travel.getDriver().getTotalTrips();

        if (totalTrips == null) totalTrips = 0;

        // n° totaltrips armazenado + 1 da viagem recentemnete feita
        int newValueOfTotalTrips = totalTrips += 1;

        driverRepository.updateTotalTrips(newValueOfTotalTrips);
    }

    /*
     * carrega os dados iniciais da viagem para o cache
     * assim que a viagem é iniciada
     */
    private void loadTravelDataToCache(Travel travel, Student student, StudentTravel studentTravel) {
        TravelPeriod travelPeriod = travel.getTravelPeriod();

        // recupera a associação student - routestop com base na rota padrão da atual viagem + o período
        Optional<StudentRouteStopAssignment> studentRouteStopAssignment = student.getStudentRouteStopAssignments().stream()
                .filter(assignment -> assignment.getStandardRoute().getId().equals(travel.getStandardRoute().getId()))
                .filter(assignment -> assignment.getTravelPeriod().equals(travelPeriod))
                .findFirst();

        studentRouteStopAssignment.ifPresent(assignment -> {
            RouteStop routeStop = assignment.getRouteStop();

            // recupera o status da associação do estudante com o ponto de parada com base
            StudentTravelRouteStopStatus studentTravelRouteStopStatus = routeStop.getStudentTravelRouteStops().stream()
                    .filter(stop -> stop.getStudentTravel().getId().equals(studentTravel.getId()))
                    .map(StudentTravelRouteStop::getStudentTravelRouteStopStatus)
                    .findFirst()
                    .orElse(StudentTravelRouteStopStatus.EXPECTED); // expected como padrão;

            StudentTravelRouteStopTrackingCacheDTO studentTravelRouteStopTrackingCacheDTO = new StudentTravelRouteStopTrackingCacheDTO(
                    studentTravel.getId(),
                    student.getId(),
                    travel.getId(),
                    routeStop.getId(),
                    routeStop.getLatitude(),
                    routeStop.getLongitude(),
                    travelPeriod,
                    studentTravelRouteStopStatus,
                    MONITORING_THRESHOLD
            );

            // salva o cache no redis
            travelTrackingStaticCacheService.saveStudentTravelTrackingData(studentTravelRouteStopTrackingCacheDTO);
        });

    }

    /*
    * verifica se o estudante possui RouteStops válidos com base na rota padrão da viagem
    * */
    private boolean isRouteStopCompatible(Travel travel, StudentTravel studentTravel) {
        StandardRoute standardRoute = travel.getStandardRoute();

        // ids dos pontos de parda vinculados ao estudante
        List<UUID> studentRouteStopIds = studentTravel.getStudentTravelRouteStops().stream()
                .map(routeStopIds -> routeStopIds.getRouteStop().getId()).toList();

        // verifica se existe algum ponto de parada do estudante vinculado na rota padrão
        return standardRoute.getStudentRouteStopAssignments().stream()
                .anyMatch(id -> studentRouteStopIds.contains(id.getRouteStop().getId()));
    }
}
