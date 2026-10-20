package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.DocumentCategory;
import com.travel_system.backend_app.model.enums.DocumentStatus;
import com.travel_system.backend_app.model.enums.DocumentType;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "document_table")
@EntityListeners(AuditingEntityListener.class)
public class Document extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID studentId;
    private UUID driverId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DocumentType documentType;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DocumentStatus documentStatus;

    private UUID healthConditionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentCategory documentCategory;

    @Column(nullable = false, length = 512)
    private String storageKey;
    @Column(nullable = false)
    private String originalFilename;
    @Column(nullable = false)
    private String contentType;
    @Column(nullable = false)
    private long sizeBytes;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(length = 500)
    private String rejectionReason;

    @Column(nullable = false)
    private UUID uploadedBy;
    private UUID reviewedBy;

    private Instant reviewedAt;
    private LocalDate validUntil;

    private Integer lastReminderStage;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public Document() {
    }

    public Document(UUID id, UUID studentId, UUID driverId, DocumentType documentType, DocumentStatus documentStatus, UUID healthConditionId, DocumentCategory documentCategory, String storageKey, String originalFilename, String contentType, long sizeBytes, String sha256, String rejectionReason, UUID uploadedBy, UUID reviewedBy, Instant reviewedAt, LocalDate validUntil, Integer lastReminderStage, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.studentId = studentId;
        this.driverId = driverId;
        this.documentType = documentType;
        this.documentStatus = documentStatus;
        this.healthConditionId = healthConditionId;
        this.documentCategory = documentCategory;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.rejectionReason = rejectionReason;
        this.uploadedBy = uploadedBy;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.validUntil = validUntil;
        this.lastReminderStage = lastReminderStage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public DocumentStatus getDocumentStatus() {
        return documentStatus;
    }

    public void setDocumentStatus(DocumentStatus documentStatus) {
        this.documentStatus = documentStatus;
    }

    public UUID getHealthConditionId() {
        return healthConditionId;
    }

    public void setHealthConditionId(UUID healthConditionId) {
        this.healthConditionId = healthConditionId;
    }

    public DocumentCategory getDocumentCategory() {
        return documentCategory;
    }

    public void setDocumentCategory(DocumentCategory documentCategory) {
        this.documentCategory = documentCategory;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(UUID uploadedBy) {
        this.uploadedBy = uploadedBy;
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

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public Integer getLastReminderStage() {
        return lastReminderStage;
    }

    public void setLastReminderStage(Integer lastReminderStage) {
        this.lastReminderStage = lastReminderStage;
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
