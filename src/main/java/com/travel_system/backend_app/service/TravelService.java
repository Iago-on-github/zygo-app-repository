package com.travel_system.backend_app.service;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.exceptions.*;
import com.travel_system.backend_app.model.*;
import com.travel_system.backend_app.model.dtos.request.TravelRequestDTO;
import com.travel_system.backend_app.model.dtos.response.DriverResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StudentTravelResponseDTO;
import com.travel_system.backend_app.model.dtos.response.TravelResponseDTO;
import com.travel_system.backend_app.model.dtos.mapboxApi.RouteDetailsDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.repository.query.ParameterOutOfBoundsException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
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

    private final Logger log = LoggerFactory.getLogger(TravelService.class);

    public TravelService(TravelRepository travelRepository, StudentTravelRepository studentTravelRepository, StudentRepository studentRepository, DriverRepository driverRepository, MapboxAPIService mapboxAPIService, RedisTrackingService redisTrackingService, TravelReportsRepository travelReportsRepository, TravelLocationHistoryRepository travelLocationHistoryRepository, PolylineService polylineService) {
        this.travelRepository = travelRepository;
        this.studentTravelRepository = studentTravelRepository;
        this.studentRepository = studentRepository;
        this.driverRepository = driverRepository;
        this.mapboxAPIService = mapboxAPIService;
        this.redisTrackingService = redisTrackingService;
        this.travelReportsRepository = travelReportsRepository;
        this.travelLocationHistoryRepository = travelLocationHistoryRepository;
        this.polylineService = polylineService;
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

        travel.setTravelStatus(TravelStatus.PENDING);
        travel.setDriver(driver);
        travel.setStartHourTravel(Instant.now());

        travelRepository.save(travel);

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

        // adiciona viagem ativa ao redis para métricas de self-health do sistema
        redisTrackingService.addActiveTravel(travelId);

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

        actualTrip.getStudentTravels().forEach(studentTravel -> {
            if (studentTravel.isEmbark()) {
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
                .map(t -> Point.fromLngLat(t.getLatitude(), t.getLongitude())).toList();

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

        // deleta os polylines para evitar lixo no banco
        // obs.: passível de usar tarefas agendadas para apagar somente dps de um determinado tempo
        travelLocationHistoryRepository.deleteAllByTravelId(travelId);

        travelRepository.save(actualTrip);

        redisTrackingService.clearTravelLocationCache(travelId);

        log.info("Viagem: {} encerrada com sucesso", travelId);
    }

    @Transactional
    public void joinTravel(UUID travelId, String studentEmail) {
        if (travelId == null || studentEmail == null) {
            throw new IllegalArgumentException("[joinTravel] travelId " + travelId + " ou studentEmail " + studentEmail + " vindo nulos.");
        }

        // realiza vínculo estudante-viagem (estudante entra na viagem)
        Travel trip = travelRepository.getReferenceById(travelId);

        if (!(trip.getTravelStatus() == TravelStatus.TRAVELLING)) {
            throwTravelException("Viagem " + travelId + " não está em andamento.");
        }

        Student student = studentRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new EntityNotFoundException("Estudante com o email " + studentEmail + " não encontrado"));

        boolean isStudentAlreadyLinked = trip.getStudentTravels().stream()
                .anyMatch(st -> st.getStudent().getId().equals(student.getId()));

        if (isStudentAlreadyLinked) {
            throw new StudentAlreadyLinkedToTrip("Estudante " + studentEmail + " já vinculado à viagem:" + travelId);
        }

        persistStudentLink(trip, student);
    }

    @Transactional
    public void leaveTravel(UUID travelId, String studentEmail) {
        if (travelId == null || studentEmail == null) {
            throw new IllegalArgumentException("[joinTravel] travelId " + travelId +  " ou studentEmail "+ studentEmail + " vindo nulos");
        }

        // Remove um estudante de uma viagem, registrando o desembarque.
        Travel trip = travelRepository.getReferenceById(travelId);

        if (!(trip.getTravelStatus() == TravelStatus.TRAVELLING)) {
            throw new TravelException("Viagem " + travelId + " não está em andamento.");
        }

        Student student = studentRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new EntityNotFoundException("Estudante com email " + studentEmail + " nao encontrado"));

        boolean isStudentLinked = trip.getStudentTravels().stream()
                .filter(st -> st.isEmbark() && st.getStudent() != null)
                .anyMatch(st -> st.getStudent().getId().equals(student.getId()));

        if (!isStudentLinked) throw new TravelStudentAssociationNotFoundException("Estudante " + studentEmail + " não está ATIVO na viagem.");

        deactivateStudentLink(trip, student);
    }

    public Set<StudentTravelResponseDTO> linkedStudentTravel(UUID travelId) {
        Travel travel = travelRepository.findByIdWithStudents(travelId)
                .orElseThrow(() -> new EntityNotFoundException("Viagem não encontrada: " + travelId));

        Set<StudentTravel> linkedStudents = travel.getStudentTravels();

        if (linkedStudents == null) throw new StudentNotLinkedToTripException("Nenhum estudante vincualado à viagem");

        return linkedStudents.stream().map(this::studentTravelMapper).collect(Collectors.toSet());
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

    // MÉTODOS AUXILIARES
    // MÉTODOS AUXILIARES
    // MÉTODOS AUXILIARES

    private StudentTravelResponseDTO studentTravelMapper(StudentTravel studentTravel) {
        return new StudentTravelResponseDTO(
                studentTravel.getId(),
                studentTravel.getTravel().getId(),
                studentTravel.getStudent().getId(),
                studentTravel.getEmbarkHour(),
                studentTravel.getDisembarkHour(),
                studentTravel.getPosition());
    }

    private void persistStudentLink(Travel actualTrip, Student student) {
        StudentTravel studentTravel = new StudentTravel();

        studentTravel.setTravel(actualTrip);
        studentTravel.setStudent(student);
        studentTravel.setEmbark(true);
        studentTravel.setEmbarkHour(Instant.now());

        studentTravelRepository.save(studentTravel);
    }

    private void deactivateStudentLink(Travel actualTrip, Student student) {
        StudentTravel studentTravel = studentTravelRepository.findByTravelIdAndStudentId(actualTrip.getId(), student.getId())
                .orElseThrow(() -> new TravelStudentAssociationNotFoundException("[leaveTravel] Vínculo aluno-viagem não encontrado."));

        studentTravel.setEmbark(false);
        studentTravel.setDisembarkHour(Instant.now());

        studentTravelRepository.save(studentTravel);
    }

    private void throwTravelException(String msg) {
        throw new TravelException(msg);
    }

    private TravelResponseDTO travelConverted(Travel travel) {
        DriverResponseDTO driverResponseDTO = driverMapper(travel.getDriver());
        return new TravelResponseDTO(
                travel.getId(),
                travel.getTravelStatus(),
                driverResponseDTO,
                travel.getStudentTravels(),
                travel.getStartHourTravel(),
                travel.getEndHourTravel()
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
                driver.getTotalTrips());
    }
}
