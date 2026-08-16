package com.travel_system.backend_app.model;

import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "standard_route")
public class StandardRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String routeName;
    private String routeDescription;
    Double originLatitude;
    Double originLongitude;
    Double destinationLatitude;
    Double destinationLongitude;
    @Column(columnDefinition = "text")
    private String standardGeometry;
    @Enumerated(EnumType.STRING)
    private TravelPeriod travelPeriod;
    @OneToMany(mappedBy = "standardRoute")
    private List<Travel> travels = new ArrayList<>();
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    @OneToMany(mappedBy = "standardRoute", cascade = CascadeType.ALL, orphanRemoval = true) // orphanRemoval = true faz com que os assignments antigos que foram removidos sejam excluídos
    @OrderBy("sequence ASC")
    private List<RouteStopAssignment> routeStopAssignments = new ArrayList<>();

    @OneToMany(mappedBy = "standardRoute", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<StudentRouteStopAssignment> studentRouteStopAssignments = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private GeneralStatus status;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public StandardRoute() {
    }

    public StandardRoute(UUID id, String routeName, String routeDescription, Double originLatitude, Double originLongitude, Double destinationLatitude, Double destinationLongitude, String standardGeometry, TravelPeriod travelPeriod, Customer customer, GeneralStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.routeName = routeName;
        this.routeDescription = routeDescription;
        this.originLatitude = originLatitude;
        this.originLongitude = originLongitude;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.standardGeometry = standardGeometry;
        this.travelPeriod = travelPeriod;
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

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public String getRouteDescription() {
        return routeDescription;
    }

    public void setRouteDescription(String routeDescription) {
        this.routeDescription = routeDescription;
    }

    public Double getOriginLatitude() {
        return originLatitude;
    }

    public void setOriginLatitude(Double originLatitude) {
        this.originLatitude = originLatitude;
    }

    public Double getOriginLongitude() {
        return originLongitude;
    }

    public void setOriginLongitude(Double originLongitude) {
        this.originLongitude = originLongitude;
    }

    public Double getDestinationLatitude() {
        return destinationLatitude;
    }

    public void setDestinationLatitude(Double destinationLatitude) {
        this.destinationLatitude = destinationLatitude;
    }

    public Double getDestinationLongitude() {
        return destinationLongitude;
    }

    public void setDestinationLongitude(Double destinationLongitude) {
        this.destinationLongitude = destinationLongitude;
    }

    public String getStandardGeometry() {
        return standardGeometry;
    }

    public void setStandardGeometry(String standardGeometry) {
        this.standardGeometry = standardGeometry;
    }

    public TravelPeriod getTravelPeriod() {
        return travelPeriod;
    }

    public void setTravelPeriod(TravelPeriod travelPeriod) {
        this.travelPeriod = travelPeriod;
    }

    public List<Travel> getTravels() {
        return travels;
    }

    public void setTravels(List<Travel> travels) {
        this.travels = travels;
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

    public List<StudentRouteStopAssignment> getStudentRouteStopAssignments() {
        return studentRouteStopAssignments;
    }

    public void setStudentRouteStopAssignments(List<StudentRouteStopAssignment> studentRouteStopAssignments) {
        this.studentRouteStopAssignments = studentRouteStopAssignments;
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


}
