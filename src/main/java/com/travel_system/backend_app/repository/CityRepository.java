package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.City;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CityRepository extends JpaRepository<City, UUID> {
    List<City> findByStatus(@Param("status") GeneralStatus status);
}
