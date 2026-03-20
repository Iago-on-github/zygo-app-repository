package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.TravelLocationHistory;
import com.travel_system.backend_app.model.dtos.route.LocationPointDTO;
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

    @Query("SELECT new com.travel_system.backend_app.model.dtos.route.LocationPointDTO(t.latitude, t.longitude) " +
            "FROM TravelLocationHistory t WHERE t.travelId = :travelId ORDER BY t.timestamp ASC")
    List<LocationPointDTO> findLatLongByTravelIdAsc(UUID travelId, Pageable pageable);
}
