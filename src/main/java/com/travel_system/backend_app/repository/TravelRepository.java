package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.StandardRoute;
import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.dtos.StudentTrackingPositionDTO;
import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.model.enums.TravelStatus;
import com.travel_system.backend_app.service.TravelService;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;

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

    boolean existsByIdAndDriverId(UUID travelId, UUID driverId);

    boolean existsByDriverIdAndTravelStatusIn(UUID driverId, List<TravelStatus> status);

    @Query("SELECT t.standardRoute FROM Travel t WHERE t.id = :travelId")
    StandardRoute findStandardRouteByTravelId(@Param("travelId") UUID travelId);

    @Query("SELECT COUNT (t) > 0 FROM Travel t WHERE t.driver.id = :driverId and t.travelStatus = TravelStatus.SCHEDULED")
    boolean existsByDriverIdAndScheduledTrip(@Param("driverId") UUID driverId);

    @Query("SELECT t.id FROM Travel t WHERE t.travelStatus = TravelStatus.SCHEDULED AND t.scheduledStartAt <= :now")
    List<UUID> findDueTravels(@Param("now") Instant now);

    @Query("SELECT t.id, t.scheduledStartAt FROM Travel t WHERE t.travelStatus = :status AND t.scheduledStartAt >= :now AND t.scheduledStartAt <= :nowPlusThreshold")
    List<UUID> findScheduledTravelsWithinTimeWindow(@Param("status") TravelStatus status, @Param("now") Instant now, @Param("nowPlusThreshold") Instant nowPlusThreshold);

    @Query("SELECT COUNT(t) > 0 FROM Travel t JOIN t.studentTravels st WHERE st.student.id = :studentId AND t.travelStatus IN :blockingStatuses")
    boolean existsByStudentIdAndStatusIn(@Param("studentId") UUID studentId, @Param("blockingStatuses") List<TravelStatus> blockingStatuses);
}
