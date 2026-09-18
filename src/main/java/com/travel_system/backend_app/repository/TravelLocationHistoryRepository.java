package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.TravelLocationHistory;
import com.travel_system.backend_app.model.dtos.route.LocationPointDTO;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.UUID;

@Repository
public interface TravelLocationHistoryRepository extends JpaRepository<TravelLocationHistory, UUID> {
    List<TravelLocationHistory> findAllByTravelIdOrderByTimestampAsc(UUID travelId);

    void deleteAllByTravelId(UUID travelId);

    @Query("SELECT new com.travel_system.backend_app.model.dtos.route.LocationPointDTO(t.latitude, t.longitude, t.timestamp) " +
            "FROM TravelLocationHistory t WHERE t.travelId = :travelId ORDER BY t.timestamp ASC")
    Page<LocationPointDTO> findLatLongByTravelIdAsc(@Param("travelId") UUID travelId, Pageable pageable);
}
