package com.travel_system.backend_app.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "TRAVEL_LOCATION_HISTORY", indexes = {
        @Index(name = "idx_travel_timestamp", columnList = "travelId, recordedAt")
})
public class TravelLocationHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID travelId;
    private UUID cityId;
    private Double latitude;
    private Double longitude;
    @Column(name = "recorded_at")
    private Instant timestamp;

    public TravelLocationHistory() {
    }

    public TravelLocationHistory(UUID travelId, UUID cityId, Double latitude, Double longitude, Instant timestamp) {
        this.travelId = travelId;
        this.cityId = cityId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTravelId() {
        return travelId;
    }

    public void setTravelId(UUID travelId) {
        this.travelId = travelId;
    }

    public UUID getCityId() {
        return cityId;
    }

    public void setCityId(UUID cityId) {
        this.cityId = cityId;
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
