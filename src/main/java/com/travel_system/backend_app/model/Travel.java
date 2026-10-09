package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.TravelDirection;
import com.travel_system.backend_app.model.enums.TravelPeriod;
import com.travel_system.backend_app.model.enums.TravelStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "travels_data")
public class Travel extends BaseTenantEntity {
    // status + identificação
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Enumerated(value = EnumType.STRING)
    private TravelStatus travelStatus;
    @ManyToOne
    @JoinColumn(name = "driver_id")
    private Driver driver;
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;
    @OneToMany(mappedBy = "travel")
    private Set<StudentTravel> studentTravels = new HashSet<>();
    @Enumerated(value = EnumType.STRING)
    private TravelPeriod travelPeriod;
    @Enumerated(EnumType.STRING)
    private TravelDirection travelDirection;
    @Column(length = 100)
    private String cancelledReason;
    private String travelScheduleCreatedBy;
    private Instant createdAt;
    private Instant cancelledAt;
    private Instant startHourTravelAt;
    private Instant scheduledStartAt;
    private Instant endHourTravelAt;

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

    @Version
    private Long version;

    public Travel() {
    }

    public Travel(UUID id, TravelStatus travelStatus, Driver driver, Vehicle vehicle, TravelPeriod travelPeriod, TravelDirection travelDirection, String cancelledReason, String travelScheduleCreatedBy, Instant createdAt, Instant cancelledAt, Instant startHourTravelAt, Instant scheduledStartAt, Instant endHourTravelAt, String polylineRoute, Double duration, Double distance, String destinationCity, Double originLatitude, Double originLongitude, Double finalLatitude, Double finalLongitude, StandardRoute standardRoute) {
        this.id = id;
        this.travelStatus = travelStatus;
        this.driver = driver;
        this.vehicle = vehicle;
        this.travelPeriod = travelPeriod;
        this.travelDirection = travelDirection;
        this.cancelledReason = cancelledReason;
        this.travelScheduleCreatedBy = travelScheduleCreatedBy;
        this.createdAt = createdAt;
        this.cancelledAt = cancelledAt;
        this.startHourTravelAt = startHourTravelAt;
        this.scheduledStartAt = scheduledStartAt;
        this.endHourTravelAt = endHourTravelAt;
        this.polylineRoute = polylineRoute;
        this.duration = duration;
        this.distance = distance;
        this.destinationCity = destinationCity;
        this.originLatitude = originLatitude;
        this.originLongitude = originLongitude;
        this.finalLatitude = finalLatitude;
        this.finalLongitude = finalLongitude;
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

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
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

    public TravelDirection getTravelDirection() {
        return travelDirection;
    }

    public void setTravelDirection(TravelDirection travelDirection) {
        this.travelDirection = travelDirection;
    }

    public String getCancelledReason() {
        return cancelledReason;
    }

    public void setCancelledReason(String cancelledReason) {
        this.cancelledReason = cancelledReason;
    }

    public String getTravelScheduleCreatedBy() {
        return travelScheduleCreatedBy;
    }

    public void setTravelScheduleCreatedBy(String travelScheduleCreatedBy) {
        this.travelScheduleCreatedBy = travelScheduleCreatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Instant getStartHourTravelAt() {
        return startHourTravelAt;
    }

    public void setStartHourTravelAt(Instant startHourTravelAt) {
        this.startHourTravelAt = startHourTravelAt;
    }

    public Instant getScheduledStartAt() {
        return scheduledStartAt;
    }

    public void setScheduledStartAt(Instant scheduledStartAt) {
        this.scheduledStartAt = scheduledStartAt;
    }

    public Instant getEndHourTravelAt() {
        return endHourTravelAt;
    }

    public void setEndHourTravelAt(Instant endHourTravelAt) {
        this.endHourTravelAt = endHourTravelAt;
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

    public String getDestinationCity() {
        return destinationCity;
    }

    public void setDestinationCity(String destinationCity) {
        this.destinationCity = destinationCity;
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

    public StandardRoute getStandardRoute() {
        return standardRoute;
    }

    public void setStandardRoute(StandardRoute standardRoute) {
        this.standardRoute = standardRoute;
    }
}
