package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.StudentTravelRouteStop;
import com.travel_system.backend_app.model.enums.StudentTravelRouteStopStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface StudentTravelRouteStopRepository extends JpaRepository<StudentTravelRouteStop, UUID> {
    /*
    * realiza o update do STATUS + os parâmetros de referência para o desvínculo do estudante pelo algoritmo de desembarque por routeStop
    * */
    @Modifying
    @Query("""
    UPDATE StudentTravelRouteStop s SET 
        s.studentTravelRouteStopStatus = :status, 
        s.lastValidatedAt = :lastValidatedAt, 
        s.reachedAt = :reachedAt
        WHERE s.studentTravel.id = :studentTravelId
        AND s.routeStop.id =:routeStopId
    
""")
    void updateStatus(
            @Param("studentTravelId") UUID studentTravelId,
            @Param("routeStopId") UUID routeStopId,
            @Param("status") StudentTravelRouteStopStatus status,
            @Param("lastValidatedAt") Instant lastValidatedAt,
            @Param("reachedAt") Instant reachedAt);
}
