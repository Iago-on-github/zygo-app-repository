package com.travel_system.backend_app.model;

import com.travel_system.backend_app.model.enums.SensitiveOperationStatus;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sensitive_operations_table")
public class SensitiveOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Enumerated(EnumType.STRING)
    private SensitiveOperationType sensitiveOperationType;
    private String requestedByUserAccountEmail;
    private String payload;
    private String verificationTokenHash;
    @Enumerated(EnumType.STRING)
    private SensitiveOperationStatus sensitiveOperationStatus;
    private Instant expiresAt;
    private Instant approvedAt;
    private Instant executedAt;

    public SensitiveOperation() {
    }

    public SensitiveOperation(UUID id, SensitiveOperationType sensitiveOperationType, String requestedByUserAccountEmail, String payload, String verificationTokenHash, SensitiveOperationStatus sensitiveOperationStatus, Instant expiresAt, Instant approvedAt, Instant executedAt) {
        this.id = id;
        this.sensitiveOperationType = sensitiveOperationType;
        this.requestedByUserAccountEmail = requestedByUserAccountEmail;
        this.payload = payload;
        this.verificationTokenHash = verificationTokenHash;
        this.sensitiveOperationStatus = sensitiveOperationStatus;
        this.expiresAt = expiresAt;
        this.approvedAt = approvedAt;
        this.executedAt = executedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public SensitiveOperationType getSensitiveOperationType() {
        return sensitiveOperationType;
    }

    public void setSensitiveOperationType(SensitiveOperationType sensitiveOperationType) {
        this.sensitiveOperationType = sensitiveOperationType;
    }

    public String getRequestedByUserAccountEmail() {
        return requestedByUserAccountEmail;
    }

    public void setRequestedByUserAccountEmail(String requestedByUserAccountEmail) {
        this.requestedByUserAccountEmail = requestedByUserAccountEmail;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getVerificationTokenHash() {
        return verificationTokenHash;
    }

    public void setVerificationTokenHash(String verificationTokenHash) {
        this.verificationTokenHash = verificationTokenHash;
    }

    public SensitiveOperationStatus getSensitiveOperationStatus() {
        return sensitiveOperationStatus;
    }

    public void setSensitiveOperationStatus(SensitiveOperationStatus sensitiveOperationStatus) {
        this.sensitiveOperationStatus = sensitiveOperationStatus;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }
}


/*
* realiza o controle global de operações críticas/sensíveis no sistema
* implementa verficações via header, set-up inicial e realiza aprovações das operações
* */
