package com.travel_system.backend_app.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "TRAVEL_DATA_REPORT")
public class TravelReports {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @OneToOne
    @MapsId
    private Travel travel;
    private Double distanceTraveled;
    private Double durationInMinutes;
    @Column(columnDefinition = "text")
    private String actualPath;
    private Instant generatedAt;
    private int busExpectedStudents;
    private int busActualOccupancy;
    private int occupancyPercentage;

    public TravelReports() {
    }

    public TravelReports(Travel travel, Double distanceTraveled, Double durationInMinutes, String actualPath, Instant generatedAt, int busExpectedStudents, int busActualOccupancy, int occupancyPercentage) {
        this.travel = travel;
        this.distanceTraveled = distanceTraveled;
        this.durationInMinutes = durationInMinutes;
        this.actualPath = actualPath;
        this.generatedAt = generatedAt;
        this.busExpectedStudents = busExpectedStudents;
        this.busActualOccupancy = busActualOccupancy;
        this.occupancyPercentage = occupancyPercentage;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Travel getTravel() {
        return travel;
    }

    public void setTravel(Travel travel) {
        this.travel = travel;
    }

    public Double getDistanceTraveled() {
        return distanceTraveled;
    }

    public void setDistanceTraveled(Double distanceTraveled) {
        this.distanceTraveled = distanceTraveled;
    }

    public Double getDurationInMinutes() {
        return durationInMinutes;
    }

    public void setDurationInMinutes(Double durationInMinutes) {
        this.durationInMinutes = durationInMinutes;
    }

    public String getActualPath() {
        return actualPath;
    }

    public void setActualPath(String actualPath) {
        this.actualPath = actualPath;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public int getBusExpectedStudents() {
        return busExpectedStudents;
    }

    public void setBusExpectedStudents(int busExpectedStudents) {
        this.busExpectedStudents = busExpectedStudents;
    }

    public int getBusActualOccupancy() {
        return busActualOccupancy;
    }

    public void setBusActualOccupancy(int busActualOccupancy) {
        this.busActualOccupancy = busActualOccupancy;
    }

    public int getOccupancyPercentage() {
        return occupancyPercentage;
    }

    public void setOccupancyPercentage(int occupancyPercentage) {
        this.occupancyPercentage = occupancyPercentage;
    }
}
