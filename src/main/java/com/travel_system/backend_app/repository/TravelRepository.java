package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.Travel;
import com.travel_system.backend_app.model.enums.TravelStatus;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TravelRepository extends JpaRepository<Travel, UUID> {

    @Query("SELECT t FROM Travel t LEFT JOIN FETCH t.studentTravels WHERE t.id = :id")
    Optional<Travel> findByIdWithStudents(@Param("id") UUID id);

    boolean existsByIdAndTravelStatus(String travelId, TravelStatus travelStatus);

    boolean existsByIdAndDriverId(UUID travelId, UUID driverId);

    boolean existsByDriverIdAndTravelStatusIn(UUID driverId, List<TravelStatus> status);
}
