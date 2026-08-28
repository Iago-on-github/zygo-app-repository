package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.RouteStop;
import com.travel_system.backend_app.model.StudentRouteStopAssignment;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface StudentRouteStopAssignmentRepository extends JpaRepository<StudentRouteStopAssignment, UUID> {

    List<StudentRouteStopAssignment> findByStudentId(UUID studentId);

    long countByStudentId(UUID studentId);

    Optional<StudentRouteStopAssignment> findByStudentIdAndStandardRouteId(UUID studentId, UUID standardRouteId);

    @Query("""
        SELECT COUNT(s) > 0 
        FROM StudentRouteStopAssignment s 
        JOIN s.standardRoute sr 
        WHERE s.student.id = :studentId 
        AND :travelPeriod MEMBER OF sr.travelPeriods
                """)
    boolean existsByStudentIdAndStandardRouteTravelPeriods(UUID studentId, TravelPeriod travelPeriod);

    Optional<StudentRouteStopAssignment> findByStudentIdAndStandardRouteIdAndRouteStopId(UUID studentId, UUID standardRouteId, UUID routeStopId);

    @Query("""
    SELECT DISTINCT rsa
    FROM StudentRouteStopAssignment rsa
    JOIN rsa.standardRoute sr
    JOIN sr.travelPeriods tp
    LEFT JOIN FETCH rsa.routeStop rs
    LEFT JOIN FETCH rs.studentRouteStopAssignments
    WHERE rsa.student.id = :studentId
      AND sr.id = :standardRouteId
      AND tp = :travelPeriod
      AND rs.customerId = :customerId
""")
    Optional<StudentRouteStopAssignment> findAssignmentByStudentRouteAndPeriod(
            @Param("studentId") UUID studentId,
            @Param("standardRouteId") UUID standardRouteId,
            @Param("travelPeriod") TravelPeriod travelPeriod,
            @Param("customerId") UUID customerId);

    Set<StudentRouteStopAssignment> findByRouteStopIdAndStandardRouteId(UUID routeStopId, UUID standardRouteId);

    @Query("""
    SELECT COUNT(a) > 0 
    FROM StudentRouteStopAssignment a 
    WHERE a.student.id = :studentId 
      AND a.standardRoute.id = :standardRouteId 
      AND a.travelPeriod = :travelPeriod 
      AND a.id != :currentAssignmentId
""")
    boolean existsByStudentIdAndStandardRouteIdAndTravelPeriodAndIdNot(
            @Param("studentId") UUID studentId,
            @Param("standardRouteId") UUID standardRouteId,
            @Param("travelPeriod") TravelPeriod travelPeriod,
            @Param("currentAssignmentId") UUID currentAssignmentId
    );
}
