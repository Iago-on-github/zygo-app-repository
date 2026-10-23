package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.DocumentType;
import com.travel_system.backend_app.model.enums.RegistrationReviewOutcome;
import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "registration_document_audit_table")
public class RegistrationDocumentAudit extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID registrationRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType documentType;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false)
    private UUID uploadedBy;
    @Column(nullable = false)
    private Instant uploadedAt;

    @Column(nullable = false)
    private UUID reviewedBy;
    @Column(nullable = false)
    private Instant reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationReviewOutcome outcome;

    public RegistrationDocumentAudit() {
    }

    public RegistrationDocumentAudit(UUID id, UUID registrationRequestId, DocumentType documentType, String sha256, UUID uploadedBy, Instant uploadedAt, UUID reviewedBy, Instant reviewedAt, RegistrationReviewOutcome outcome) {
        this.id = id;
        this.registrationRequestId = registrationRequestId;
        this.documentType = documentType;
        this.sha256 = sha256;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.outcome = outcome;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRegistrationRequestId() {
        return registrationRequestId;
    }

    public void setRegistrationRequestId(UUID registrationRequestId) {
        this.registrationRequestId = registrationRequestId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(UUID uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(UUID reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public RegistrationReviewOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(RegistrationReviewOutcome outcome) {
        this.outcome = outcome;
    }
}
