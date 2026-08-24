package com.travel_system.backend_app.model;

import com.travel_system.backend_app.model.enums.TravelPeriod;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "student_route_stop_assignment")
public class StudentRouteStopAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_stop_id")
    private RouteStop routeStop;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "standard_route_id")
    private StandardRoute standardRoute;
    @Enumerated(EnumType.STRING)
    @Column(name = "travel_period")
    private TravelPeriod travelPeriod;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public StudentRouteStopAssignment() {
    }

    public StudentRouteStopAssignment(UUID id, Student student, RouteStop routeStop, StandardRoute standardRoute, TravelPeriod travelPeriod, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.student = student;
        this.routeStop = routeStop;
        this.standardRoute = standardRoute;
        this.travelPeriod = travelPeriod;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public RouteStop getRouteStop() {
        return routeStop;
    }

    public void setRouteStop(RouteStop routeStop) {
        this.routeStop = routeStop;
    }

    public StandardRoute getStandardRoute() {
        return standardRoute;
    }

    public void setStandardRoute(StandardRoute standardRoute) {
        this.standardRoute = standardRoute;
    }

    public TravelPeriod getTravelPeriod() {
        return travelPeriod;
    }

    public void setTravelPeriod(TravelPeriod travelPeriod) {
        this.travelPeriod = travelPeriod;
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
