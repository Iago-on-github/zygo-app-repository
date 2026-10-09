package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.VehicleType;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicle_table")
@EntityListeners(AuditingEntityListener.class)
public class Vehicle extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(length = 10, unique = true, nullable = false)
    private String vehicleNumber;
    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;
    private String vehicleImage;
    @Column(length = 7, unique = true, nullable = false)
    private String numberPlate;
    private String color;
    private Integer totalTrips;
    private GeneralStatus status = GeneralStatus.ACTIVE;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public Vehicle() {
    }

    public Vehicle(Instant updatedAt, Instant createdAt, GeneralStatus status, Integer totalTrips, String color, String numberPlate, String vehicleImage, VehicleType vehicleType, String vehicleNumber, UUID id) {
        this.updatedAt = updatedAt;
        this.createdAt = createdAt;
        this.status = status;
        this.totalTrips = totalTrips;
        this.color = color;
        this.numberPlate = numberPlate;
        this.vehicleImage = vehicleImage;
        this.vehicleType = vehicleType;
        this.vehicleNumber = vehicleNumber;
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getVehicleImage() {
        return vehicleImage;
    }

    public void setVehicleImage(String vehicleImage) {
        this.vehicleImage = vehicleImage;
    }

    public String getNumberPlate() {
        return numberPlate;
    }

    public void setNumberPlate(String numberPlate) {
        this.numberPlate = numberPlate;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getTotalTrips() {
        return totalTrips;
    }

    public void setTotalTrips(Integer totalTrips) {
        this.totalTrips = totalTrips;
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
