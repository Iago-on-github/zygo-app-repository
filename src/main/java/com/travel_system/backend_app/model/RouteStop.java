package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "route_stop")
public class RouteStop extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    private String description;
    private Double latitude;
    private Double longitude;
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;
    @OneToMany(mappedBy = "routeStop")
    private List<RouteStopAssignment> routeStopAssignments = new ArrayList<>();
    @OneToMany(mappedBy = "routeStop")
    private List<StudentRouteStopAssignment> studentRouteStopAssignments = new ArrayList<>();
    @OneToMany(mappedBy = "routeStop")
    private List<StudentTravelRouteStop> studentTravelRouteStops = new ArrayList<>();
    @Enumerated(EnumType.STRING)
    private GeneralStatus status;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public RouteStop() {
    }

    public RouteStop(UUID id, String name, String description, Double latitude, Double longitude, Customer customer, GeneralStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
        this.customer = customer;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public List<RouteStopAssignment> getRouteStopAssignments() {
        return routeStopAssignments;
    }

    public void setRouteStopAssignments(List<RouteStopAssignment> routeStopAssignments) {
        this.routeStopAssignments = routeStopAssignments;
    }

    public List<StudentTravelRouteStop> getStudentTravelRouteStops() {
        return studentTravelRouteStops;
    }

    public void setStudentTravelRouteStops(List<StudentTravelRouteStop> studentTravelRouteStops) {
        this.studentTravelRouteStops = studentTravelRouteStops;
    }

    public GeneralStatus getStatus() {
        return status;
    }

    public void setStatus(GeneralStatus status) {
        this.status = status;
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

    public List<StudentRouteStopAssignment> getStudentRouteStopAssignments() {
        return studentRouteStopAssignments;
    }

    public void setStudentRouteStopAssignments(List<StudentRouteStopAssignment> studentRouteStopAssignments) {
        this.studentRouteStopAssignments = studentRouteStopAssignments;
    }
}
