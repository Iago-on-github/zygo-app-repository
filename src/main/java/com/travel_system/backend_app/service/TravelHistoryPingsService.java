package com.travel_system.backend_app.service;

import com.travel_system.backend_app.infrastructure.TenantFilterAspect;
import com.travel_system.backend_app.model.TravelLocationHistory;
import com.travel_system.backend_app.model.dtos.request.VehicleLocationRequestDTO;
import com.travel_system.backend_app.repository.TravelLocationHistoryRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class TravelHistoryPingsService {
    private final TravelLocationHistoryRepository travelLocationHistoryRepository;
    private final TenantFilterAspect tenantFilterAspect;

    public TravelHistoryPingsService(TravelLocationHistoryRepository travelLocationHistoryRepository, TenantFilterAspect tenantFilterAspect) {
        this.travelLocationHistoryRepository = travelLocationHistoryRepository;
        this.tenantFilterAspect = tenantFilterAspect;
    }

    @Async
    public void saveTravelLocationHistoryData(String city, String travelId, Instant now, VehicleLocationRequestDTO vehicleLocation) {
        if (city == null || travelId == null) return;

        // ativa o filtro do customerId antes de qualquer acesso ao banco
        tenantFilterAspect.applyFilter();

        TravelLocationHistory travelLocationHistory = new TravelLocationHistory(
                UUID.fromString(travelId),
                UUID.fromString(city),
                vehicleLocation.latitude(),
                vehicleLocation.longitude(),
                now
        );

        travelLocationHistoryRepository.save(travelLocationHistory);
    }
}
