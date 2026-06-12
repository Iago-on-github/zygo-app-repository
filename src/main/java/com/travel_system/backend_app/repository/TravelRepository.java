package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.StudentTrackingPositionDTO;
import com.travel_system.backend_app.model.enums.TravelStatus;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TravelRepository extends JpaRepository<Travel, UUID> {

        @Query("""
        SELECT new com.travel_system.backend_app.model.dtos.StudentTrackingPositionDTO(
            s.id,
            p.latitude,
            p.longitude
        )
        FROM StudentTravel st
        JOIN st.student s
        JOIN st.position p
        WHERE st.travel.id = :travelId
    """)
        Set<StudentTrackingPositionDTO> findTrackingPositionsByTravelId(
            @Param("travelId") UUID travelId
    );

    boolean existsByIdAndTravelStatus(UUID travelId, TravelStatus travelStatus);

    boolean existsByIdAndDriverId(UUID travelId, UUID driverId);

    boolean existsByDriverIdAndTravelStatusIn(UUID driverId, List<TravelStatus> status);
}
