package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "health_consent_table")
public class HealthConsent extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID studentId;
    private UUID driverId;
    @Column(nullable = false)
    private UUID consentedBy;
    @Column(nullable = false)
    private Instant consentedAt;
    @Column(nullable = false)
    private String termVersion;
    private String purpose;
    @Column(nullable = false)
    private Instant revokedAt;

    public HealthConsent() {
    }

    public HealthConsent(UUID id, UUID studentId, UUID driverId, UUID consentedBy, Instant consentedAt, String termVersion, String purpose, Instant revokedAt) {
        this.id = id;
        this.studentId = studentId;
        this.driverId = driverId;
        this.consentedBy = consentedBy;
        this.consentedAt = consentedAt;
        this.termVersion = termVersion;
        this.purpose = purpose;
        this.revokedAt = revokedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public void setDriverId(UUID driverId) {
        this.driverId = driverId;
    }

    public UUID getConsentedBy() {
        return consentedBy;
    }

    public void setConsentedBy(UUID consentedBy) {
        this.consentedBy = consentedBy;
    }

    public Instant getConsentedAt() {
        return consentedAt;
    }

    public void setConsentedAt(Instant consentedAt) {
        this.consentedAt = consentedAt;
    }

    public String getTermVersion() {
        return termVersion;
    }

    public void setTermVersion(String termVersion) {
        this.termVersion = termVersion;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
