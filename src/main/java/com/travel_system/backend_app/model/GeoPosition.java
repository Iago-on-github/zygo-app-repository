package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "GEO_POSITION_TABLE")
public class GeoPosition extends BaseTenantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    private Double latitude;
    private Double longitude;
    private Instant timeStamp;
    @OneToOne
    @JoinColumn(name = "student_travel_id")
    private StudentTravel studentTravel;


    public GeoPosition() {
    }

    public GeoPosition(UUID id, Double latitude, Double longitude, Instant timeStamp, StudentTravel studentTravel) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timeStamp = timeStamp;
        this.studentTravel = studentTravel;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public Instant getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(Instant timeStamp) {
        this.timeStamp = timeStamp;
    }

    public StudentTravel getStudentTravel() {
        return studentTravel;
    }

    public void setStudentTravel(StudentTravel studentTravel) {
        this.studentTravel = studentTravel;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        GeoPosition that = (GeoPosition) o;
        return Objects.equals(latitude, that.latitude) && Objects.equals(longitude, that.longitude) && Objects.equals(timeStamp, that.timeStamp) && Objects.equals(studentTravel, that.studentTravel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, timeStamp, studentTravel);
    }
}
