package com.travel_system.backend_app.utils;

import com.mapbox.geojson.Point;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.TravelLocationHistory;
import com.travel_system.backend_app.model.TravelReports;
import com.travel_system.backend_app.repository.TravelLocationHistoryRepository;
import com.travel_system.backend_app.repository.TravelReportsRepository;
import com.travel_system.backend_app.service.PolylineService;
import com.travel_system.backend_app.service.RedisTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class CollectTravelReportsMetrics {
    private final Logger log = LoggerFactory.getLogger(CollectTravelReportsMetrics.class);

    private final TravelLocationHistoryRepository travelLocationHistoryRepository;
    private final RedisTrackingService redisTrackingService;
    private final TravelReportsRepository travelReportsRepository;

    private final PolylineService polylineService;

    public CollectTravelReportsMetrics(TravelLocationHistoryRepository travelLocationHistoryRepository, RedisTrackingService redisTrackingService, TravelReportsRepository travelReportsRepository, PolylineService polylineService) {
        this.travelLocationHistoryRepository = travelLocationHistoryRepository;
        this.redisTrackingService = redisTrackingService;
        this.travelReportsRepository = travelReportsRepository;
        this.polylineService = polylineService;
    }

    @Transactional
    public void collectEndTravelMetrics(Travel travel) {
        // percentual de estudantes na viagem
        int totalStudentsCount = travel.getStudentTravels().size();
        long embarkedStudentsCount = travel.getStudentTravels().stream()
                .filter(student -> student.getEmbarkHour() != null && student.isEmbark()).count();

        long percentual = 0;
        if (totalStudentsCount != 0 && embarkedStudentsCount != 0) {
            percentual = embarkedStudentsCount * 100 / totalStudentsCount;
        }

        // obtem os dados de lat/lng para formar a polyline da viagem
        List<TravelLocationHistory> travelRecorded = travelLocationHistoryRepository.findAllByTravelIdOrderByTimestampAsc(travel.getId());

        List<Point> pointList = travelRecorded.stream()
                .filter(t -> t.getLatitude() != null && t.getLongitude() != null)
                // atentar-se que, no Point, a LONGITUDE sempre será primeiro
                .map(t -> Point.fromLngLat(t.getLongitude(), t.getLatitude())).toList();

        String polylineEncoded = polylineService.formattedPolylineEncoded(pointList);

        // polyline, em cenários sem falha interna, pode retornar null caso a viagem seja encerrada muito cedo
        if (polylineEncoded == null || polylineEncoded.isBlank()) {
            log.warn("[collectEndTravelMetrics]: polyline retornando null, salvando string vazia. Viagem: {}", travel.getId());
        }

        // métricas gerais sobre a viagem (distância acumulada, duração)
        Double accumulatedDistance = Double.valueOf(redisTrackingService.getAccumulatedDistance(travel.getId()));
        Duration durationInMinutes = Duration.between(travel.getStartHourTravel(), travel.getEndHourTravel());
        double formattedDurationInMinutes = (double) durationInMinutes.toMinutes() / 60.0;

        TravelReports travelReports = new TravelReports(
                travel,
                accumulatedDistance,
                formattedDurationInMinutes,
                polylineEncoded,
                Instant.now(),
                totalStudentsCount, // expectativa de estudantes na viagem
                (int) embarkedStudentsCount, // ocupação total de estudantes embarcados
                (int) percentual);

        travelReportsRepository.save(travelReports);
    }

}

/*
* responsável por coletar métricas sobre as viagens e componentes que envolvem a mesma
* */