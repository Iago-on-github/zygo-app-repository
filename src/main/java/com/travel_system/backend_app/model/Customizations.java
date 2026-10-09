package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customizations_table")
public class Customizations extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID customerId;
    @Column(nullable = false, length = 500)
    private String description;
    @Column(nullable = false)
    private String registerBy;
    @Column(nullable = false)
    private int totalDevelopmentHours;

    @Column(nullable = false)
    private UUID contractedBy;
    @Column(nullable = false)
    private Instant contractedAt;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public Customizations() {
    }

    public Customizations(UUID id, UUID customerId, String description, String registerBy, int totalDevelopmentHours, UUID contractedBy, Instant contractedAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.description = description;
        this.registerBy = registerBy;
        this.totalDevelopmentHours = totalDevelopmentHours;
        this.contractedBy = contractedBy;
        this.contractedAt = contractedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRegisterBy() {
        return registerBy;
    }

    public void setRegisterBy(String registerBy) {
        this.registerBy = registerBy;
    }

    public int getTotalDevelopmentHours() {
        return totalDevelopmentHours;
    }

    public void setTotalDevelopmentHours(int totalDevelopmentHours) {
        this.totalDevelopmentHours = totalDevelopmentHours;
    }

    public UUID getContractedBy() {
        return contractedBy;
    }

    public void setContractedBy(UUID contractedBy) {
        this.contractedBy = contractedBy;
    }

    public Instant getContractedAt() {
        return contractedAt;
    }

    public void setContractedAt(Instant contractedAt) {
        this.contractedAt = contractedAt;
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
