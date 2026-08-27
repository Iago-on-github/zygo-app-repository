package com.travel_system.backend_app.model;

/*
* responsável por definir a ordem das paradas na rota padrão durante a viagem
* */

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "route_stop_assignment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "standard_route_id"}))
public class RouteStopAssignment extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "standard_route_id")
    private StandardRoute standardRoute;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_stop_id", nullable = false)
    private RouteStop routeStop;
    @Column(nullable = false)
    private Integer sequence;
    private boolean isOptionalSpot; // ponto de parada opcional

    public RouteStopAssignment() {
    }

    public RouteStopAssignment(UUID id, StandardRoute standardRoute, RouteStop routeStop, Integer sequence, boolean isOptionalSpot) {
        this.id = id;
        this.standardRoute = standardRoute;
        this.routeStop = routeStop;
        this.sequence = sequence;
        this.isOptionalSpot = isOptionalSpot;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public StandardRoute getStandardRoute() {
        return standardRoute;
    }

    public void setStandardRoute(StandardRoute standardRoute) {
        this.standardRoute = standardRoute;
    }

    public RouteStop getRouteStop() {
        return routeStop;
    }

    public void setRouteStop(RouteStop routeStop) {
        this.routeStop = routeStop;
    }

    public Integer getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }

    public boolean isOptionalSpot() {
        return isOptionalSpot;
    }

    public void setOptionalSpot(boolean optionalSpot) {
        isOptionalSpot = optionalSpot;
    }
}
