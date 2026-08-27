package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.StudentTravelRouteStopStatus;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_travel_route_stop")
public class StudentTravelRouteStop extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_travel_id", nullable = false)
    private StudentTravel studentTravel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_stop_id", nullable = false)
    private RouteStop routeStop;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StudentTravelRouteStopStatus studentTravelRouteStopStatus;
    private Instant lastValidatedAt;
    private Instant reachedAt;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public StudentTravelRouteStop() {
    }

    public StudentTravelRouteStop(UUID id, StudentTravel studentTravel, RouteStop routeStop, StudentTravelRouteStopStatus studentTravelRouteStopStatus, Instant lastValidatedAt, Instant reachedAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.studentTravel = studentTravel;
        this.routeStop = routeStop;
        this.studentTravelRouteStopStatus = studentTravelRouteStopStatus;
        this.lastValidatedAt = lastValidatedAt;
        this.reachedAt = reachedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public StudentTravel getStudentTravel() {
        return studentTravel;
    }

    public void setStudentTravel(StudentTravel studentTravel) {
        this.studentTravel = studentTravel;
    }

    public RouteStop getRouteStop() {
        return routeStop;
    }

    public void setRouteStop(RouteStop routeStop) {
        this.routeStop = routeStop;
    }

    public StudentTravelRouteStopStatus getStudentTravelRouteStopStatus() {
        return studentTravelRouteStopStatus;
    }

    public void setStudentTravelRouteStopStatus(StudentTravelRouteStopStatus studentTravelRouteStopStatus) {
        this.studentTravelRouteStopStatus = studentTravelRouteStopStatus;
    }

    public Instant getLastValidatedAt() {
        return lastValidatedAt;
    }

    public void setLastValidatedAt(Instant lastValidatedAt) {
        this.lastValidatedAt = lastValidatedAt;
    }

    public Instant getReachedAt() {
        return reachedAt;
    }

    public void setReachedAt(Instant reachedAt) {
        this.reachedAt = reachedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
