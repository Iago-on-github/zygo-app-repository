package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RouteStopRepository extends JpaRepository<RouteStop, UUID> {
    @Query("SELECT rs FROM RouteStop rs WHERE rs.customerId = :customerId")
    List<RouteStop> findRouteStopsByCustomerId(@Param("customerId") UUID customerId);

    Optional<RouteStop> findByName(String routeName);

    boolean existsByNameAndCustomerId(@NotNull String name, UUID customerId);

    boolean existsByNameAndCustomerIdAndIdNot(@Param("name") String name, @Param("customerId") UUID customerId, @Param("routeStopId") UUID routeStopId);
}
