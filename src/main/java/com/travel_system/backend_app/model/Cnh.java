package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.CnhCategory;
import jakarta.persistence.*;
import org.springframework.cglib.core.Local;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "cnh_driver_table")
@EntityListeners(AuditingEntityListener.class)
public class Cnh extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(length = 11, unique = true, nullable = false)
    private String cnhNumber;

    @ElementCollection(targetClass = CnhCategory.class)
    @CollectionTable(name = "cnh_category", joinColumns = @JoinColumn(name = "cnh_id"))
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Set<CnhCategory> cnhCategories = new HashSet<>();

    @OneToOne(mappedBy = "cnh")
    private Driver driver;

    private LocalDate cnhExpirationDate;
    private LocalDate cnhFirstIssueDate;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public Cnh() {
    }

    public Cnh(UUID id, String cnhNumber, Set<CnhCategory> cnhCategories, Driver driver, LocalDate cnhExpirationDate, LocalDate cnhFirstIssueDate, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.cnhNumber = cnhNumber;
        this.cnhCategories = cnhCategories;
        this.driver = driver;
        this.cnhExpirationDate = cnhExpirationDate;
        this.cnhFirstIssueDate = cnhFirstIssueDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCnhNumber() {
        return cnhNumber;
    }

    public void setCnhNumber(String cnhNumber) {
        this.cnhNumber = cnhNumber;
    }

    public Set<CnhCategory> getCnhCategories() {
        return cnhCategories;
    }

    public void setCnhCategories(Set<CnhCategory> cnhCategories) {
        this.cnhCategories = cnhCategories;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }

    public LocalDate getCnhExpirationDate() {
        return cnhExpirationDate;
    }

    public void setCnhExpirationDate(LocalDate cnhExpirationDate) {
        this.cnhExpirationDate = cnhExpirationDate;
    }

    public LocalDate getCnhFirstIssueDate() {
        return cnhFirstIssueDate;
    }

    public void setCnhFirstIssueDate(LocalDate cnhFirstIssueDate) {
        this.cnhFirstIssueDate = cnhFirstIssueDate;
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
