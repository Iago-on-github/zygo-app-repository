package com.travel_system.backend_app.repository;

import com.travel_system.backend_app.model.RouteStopAssignment;
import com.travel_system.backend_app.model.StandardRoute;
import com.travel_system.backend_app.model.dtos.response.RouteStopAssignmentResponseDTO;
import com.travel_system.backend_app.model.dtos.response.StandardRouteResponseDTO;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface StandardRouteRepository extends JpaRepository<StandardRoute, UUID> {

    // sobreescreve o método padrão "findById" do JPA e força o carregamento dos "travelPeriods"
    @Override
    @EntityGraph(attributePaths = {"travelPeriods"})
    Optional<StandardRoute> findById(UUID id);

    // busca em apenas uma query entidades relacionadas
    @EntityGraph(attributePaths = {"routeStopAssignments", "routeStopAssignments.routeStop", "customer"})
    Page<StandardRoute> findAllByCustomerId(UUID customerId, Pageable pageable);

    boolean existsByRouteNameAndCustomerId(String name, UUID customerId);

    boolean existsByRouteNameAndCustomerIdAndIdNot(String name, UUID customerId, UUID standardRouteId);

    @Query("""
    SELECT new com.travel_system.backend_app.model.dtos.response.RouteStopAssignmentResponseDTO(
        rsa.routeStop.id,
        rsa.routeStop.name,
        rsa.sequence,
        rsa.isOptionalSpot
    )
    FROM RouteStopAssignment rsa
    WHERE rsa.standardRoute.id = :standardRouteId
    ORDER BY rsa.sequence ASC
""")
    Set<RouteStopAssignmentResponseDTO> findAssignmentsByRouteId(@Param("standardRouteId") UUID standardRouteId);

    @Query("""
        SELECT sr FROM StandardRoute sr 
        LEFT JOIN FETCH sr.travelPeriods 
        WHERE sr.id = :id 
        AND sr.status = :status
    """)
    Optional<StandardRoute> findRouteBaseByIdAndStatus(@Param("id") UUID id, @Param("status") GeneralStatus status);
}

