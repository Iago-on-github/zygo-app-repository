package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.StudentTravelRouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StudentTravelRouteStopRepository extends JpaRepository<StudentTravelRouteStop, UUID> {
}
