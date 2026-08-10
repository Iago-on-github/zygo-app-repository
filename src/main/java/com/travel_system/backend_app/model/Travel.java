package com.travel_system.backend_app.model;

import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.model.enums.TravelStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "TRAVELS_DATA")
public class Travel {
    // status + identificação
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Enumerated(value = EnumType.STRING)
    private TravelStatus travelStatus;
    @ManyToOne
    @JoinColumn(name = "driver_id")
    private Driver driver;
    @OneToMany(mappedBy = "travel")
    private Set<StudentTravel> studentTravels = new HashSet<>();
    @Enumerated(value = EnumType.STRING)
    private TravelPeriod travelPeriod;
    private Instant createdAt;
    private Instant startHourTravel;
    private Instant endHourTravel;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customerId", nullable = false)
    private Customer customer;

    // rota (estáticos)
    @Column(columnDefinition = "text")
    private String polylineRoute;
    private Double duration;
    private Double distance;

    private String destinationCity;

    // coordenadas
    private Double originLatitude;
    private Double originLongitude;
    private Double finalLatitude;
    private Double finalLongitude;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "standard_route_id")
    private StandardRoute standardRoute;

    public Travel() {
    }

    public Travel(UUID id, TravelStatus travelStatus, Driver driver, Instant createdAt, Instant startHourTravel, TravelPeriod travelPeriod, Instant endHourTravel, String polylineRoute, Double duration, Double distance, Double originLatitude, Double originLongitude, Double finalLatitude, Double finalLongitude, String destinationCity, Customer customer, StandardRoute standardRoute) {
        this.id = id;
        this.travelStatus = travelStatus;
        this.driver = driver;
        this.createdAt = createdAt;
        this.startHourTravel = startHourTravel;
        this.travelPeriod = travelPeriod;
        this.endHourTravel = endHourTravel;
        this.polylineRoute = polylineRoute;
        this.duration = duration;
        this.distance = distance;
        this.originLatitude = originLatitude;
        this.originLongitude = originLongitude;
        this.finalLatitude = finalLatitude;
        this.finalLongitude = finalLongitude;
        this.destinationCity = destinationCity;
        this.customer = customer;
        this.standardRoute = standardRoute;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public TravelStatus getTravelStatus() {
        return travelStatus;
    }

    public void setTravelStatus(TravelStatus travelStatus) {
        this.travelStatus = travelStatus;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public Set<StudentTravel> getStudentTravels() {
        return studentTravels;
    }

    public void setStudentTravels(Set<StudentTravel> studentTravels) {
        this.studentTravels = studentTravels;
    }

    public TravelPeriod getTravelPeriod() {
        return travelPeriod;
    }

    public void setTravelPeriod(TravelPeriod travelPeriod) {
        this.travelPeriod = travelPeriod;
    }

    public Instant getStartHourTravel() {
        return startHourTravel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setStartHourTravel(Instant startHourTravel) {
        this.startHourTravel = startHourTravel;
    }

    public Instant getEndHourTravel() {
        return endHourTravel;
    }

    public void setEndHourTravel(Instant endHourTravel) {
        this.endHourTravel = endHourTravel;
    }

    public String getPolylineRoute() {
        return polylineRoute;
    }

    public void setPolylineRoute(String polylineRoute) {
        this.polylineRoute = polylineRoute;
    }

    public Double getDuration() {
        return duration;
    }

    public void setDuration(Double duration) {
        this.duration = duration;
    }

    public Double getDistance() {
        return distance;
    }

    public void setDistance(Double distance) {
        this.distance = distance;
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

    public Double getFinalLatitude() {
        return finalLatitude;
    }

    public void setFinalLatitude(Double finalLatitude) {
        this.finalLatitude = finalLatitude;
    }

    public Double getFinalLongitude() {
        return finalLongitude;
    }

    public void setFinalLongitude(Double finalLongitude) {
        this.finalLongitude = finalLongitude;
    }

    public String getDestinationCity() {
        return destinationCity;
    }

    public void setDestinationCity(String destinationCity) {
        this.destinationCity = destinationCity;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public StandardRoute getStandardRoute() {
        return standardRoute;
    }

    public void setStandardRoute(StandardRoute standardRoute) {
        this.standardRoute = standardRoute;
    }
}
