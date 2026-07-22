package com.travel_system.backend_app.service;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.StudentTrackingPositionDTO;
import com.travel_system.backend_app.model.dtos.TravelPreviewDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.LiveLocationDTO;
import com.travel_system.backend_app.model.dtos.request.TravelRequestDTO;
import com.travel_system.backend_app.model.dtos.response.*;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.StudentTravelStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.repository.query.ParameterOutOfBoundsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class TravelService {

    private final TravelRepository travelRepository;
    private final StudentTravelRepository studentTravelRepository;
    private final StudentRepository studentRepository;
    private final DriverRepository driverRepository;
    private final MapboxAPIService mapboxAPIService;
    private final RedisTrackingService redisTrackingService;
    private final TravelReportsRepository travelReportsRepository;
    private final TravelLocationHistoryRepository travelLocationHistoryRepository;
    private final PolylineService polylineService;
    private final RouteCalculationService routeCalculationService;
    private final TravelCacheService travelCacheService;
    private final TravelStudentStateCacheService travelStudentStateCacheService;
    private final TravelNotificationService travelNotificationService;

    private final Logger log = LoggerFactory.getLogger(TravelService.class);

    public TravelService(TravelRepository travelRepository, StudentTravelRepository studentTravelRepository, StudentRepository studentRepository, DriverRepository driverRepository, MapboxAPIService mapboxAPIService, RedisTrackingService redisTrackingService, TravelReportsRepository travelReportsRepository, TravelLocationHistoryRepository travelLocationHistoryRepository, PolylineService polylineService, RouteCalculationService routeCalculationService, TravelCacheService travelCacheService, TravelStudentStateCacheService travelStudentStateCacheService, TravelNotificationService travelNotificationService) {
        this.travelRepository = travelRepository;
        this.studentTravelRepository = studentTravelRepository;
        this.studentRepository = studentRepository;
        this.driverRepository = driverRepository;
        this.mapboxAPIService = mapboxAPIService;
        this.redisTrackingService = redisTrackingService;
        this.travelReportsRepository = travelReportsRepository;
        this.travelLocationHistoryRepository = travelLocationHistoryRepository;
        this.polylineService = polylineService;
        this.routeCalculationService = routeCalculationService;
        this.travelCacheService = travelCacheService;
        this.travelStudentStateCacheService = travelStudentStateCacheService;
        this.travelNotificationService = travelNotificationService;
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

        if (actualTrip.getTravelStatus() == TravelStatus.FINISH) {
            throwTravelException("Não é possível prosseguir, a viagem " + travelId + " já foi finalizada.");
        } if (actualTrip.getTravelStatus() == TravelStatus.TRAVELLING) {
            throwTravelException("Não é possível prosseguir, a viagem " + travelId + " já está em andamento.");
        }

        // chama o mapboxservice para calcular a rota
        RouteDetailsDTO routeDetailsDTO = mapboxAPIService.calculateRoute(
                actualTrip.getOriginLongitude(),
                actualTrip.getOriginLatitude(),
                actualTrip.getFinalLongitude(),
                actualTrip.getFinalLatitude());

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

        int studentSize = actualTrip.getStudentTravels().size();
        long totalOccupancy = actualTrip.getStudentTravels().stream()
                .filter(student -> student.getEmbarkHour() != null).count();

        long percentual = 0;
        if (studentSize != 0 && totalOccupancy != 0) {
            percentual = totalOccupancy * 100 / studentSize;
        }

        UUID baseCustomerId = actualTrip.getCustomer().getId();

        // realiza o desvínculo de estudantes
        actualTrip.getStudentTravels().forEach(studentTravel -> {
            UUID studentsCustomerId = studentTravel.getStudent().getCustomer().getId();

            if (studentTravel.isEmbark() && isSameCustomer(baseCustomerId, studentsCustomerId)) {
                studentTravel.setEmbark(false);
                studentTravel.setDisembarkHour(Instant.now());
                studentTravelRepository.save(studentTravel);
            }

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
                studentSize,
                (int) totalOccupancy,
                (int) percentual);

        travelReportsRepository.save(travelReports);

        // envia notificação para o firebase comunicando o fim da viagem
        travelNotificationService.sendTravelEndedNotification(actualTrip);

        // deleta os polylines para evitar lixo no banco
        // obs.: passível de usar tarefas agendadas para apagar somente dps de um determinado tempo
        travelLocationHistoryRepository.deleteAllByTravelId(travelId);

        travelRepository.save(actualTrip);

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

        if (isAlreadyActive) {
            throw new StudentAlreadyLinkedToTrip("Estudante " + studentEmail + " já vinculado à viagem:" + travelId);
        }

        Travel trip = travelRepository.getReferenceById(travelId);

        if (trip.getTravelStatus() != TravelStatus.TRAVELLING) {
            throw new TravelException("Viagem " + travelId + " não está em andamento.");
        }

        Student student = studentRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new EntityNotFoundException("Estudante com email " + studentEmail + " não encontrado"));

        UUID baseCustomerId = trip.getCustomer().getId();
        UUID studentsCustomerId = student.getCustomer().getId();

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

        UUID actuallyDriverCustomerId = actualTrip.getDriver().getCustomer().getId();
        UUID driverCandidateCustomerId = driver.getCustomer().getId();

        if (!isSameCustomer(actuallyDriverCustomerId, driverCandidateCustomerId)) {
            throwTravelException("Motoristas devem pertencer ao mesmo Customer");
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

        UUID baseCustomerId = actualTrip.getCustomer().getId();

        // verifica se existem estudantes vinculados e faz a deconexão
        if (!actualTrip.getStudentTravels().isEmpty()) {
            actualTrip.getStudentTravels().forEach(studentTravel -> {
                UUID studentsCustomerId = studentTravel.getStudent().getCustomer().getId();

                if (studentTravel.isEmbark() && isSameCustomer(baseCustomerId, studentsCustomerId)) {
                    studentTravel.setEmbark(false);
                    studentTravel.setDisembarkHour(Instant.now());
                    studentTravelRepository.save(studentTravel);
                }
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

    public Set<StudentTrackingPositionDTO> linkedStudentTravel(UUID travelId) {
        long start = System.currentTimeMillis(); // debbuging

        Set<StudentTrackingPositionDTO> linkedStudents = travelRepository.findTrackingPositionsByTravelId(travelId);

        if (linkedStudents.isEmpty()) {
            throw new StudentNotLinkedToTripException("Nenhum estudante vincualado à viagem: " + travelId);
        }

        long executingTime = System.currentTimeMillis() - start;
        log.info("[linkedStudentTravel] - método que busca a viagem com estudantes. Tempo de execução: {} ", executingTime);

        return linkedStudents;
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

        long elapsed = System.currentTimeMillis() - start;
        log.info("[leaveTravel] tempo para executar o leave-travel: {}", elapsed);
    }

    private void throwTravelException(String msg) {
        throw new TravelException(msg);
    }

    private TravelResponseDTO travelConverted(Travel travel) {
        DriverResponseDTO driverResponseDTO = driverMapper(travel.getDriver());

        String arrivalTime = null;

        // Usa campo "createdAt" para exibir preview ao motorista APENAS ao criar a viagem (sem inicia-la)
        if (travel.getCreatedAt() != null && travel.getDuration() != null) {
            arrivalTime = travel.getCreatedAt()
                    .plusSeconds(travel.getDuration().longValue()).toString();
        }

        TravelPreviewDTO travelPreviewDTO = new TravelPreviewDTO(travel.getDistance(), travel.getDuration(), travel.getDestinationCity(), arrivalTime);

        return new TravelResponseDTO(
                travel.getId(),
                travel.getTravelStatus(),
                travel.getTravelPeriod(),
                driverResponseDTO,
                travel.getStudentTravels(),
                travel.getCreatedAt(),
                travel.getStartHourTravel(),
                travelPreviewDTO
        );
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
                driver.getCustomer().getId()
        );
    }
}
