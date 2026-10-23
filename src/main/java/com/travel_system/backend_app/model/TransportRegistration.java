package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.RegistrationClosedReason;
import com.travel_system.backend_app.model.enums.TransportRegistrationStatus;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/*
* entidade responsável pela matricula (registro) de usuários elegíveis para tal no sistema
* */

@Entity
@Table(name = "transport_registration_table")
@EntityListeners(AuditingEntityListener.class)
public class TransportRegistration extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID studentId;
    private UUID driverId;

    @Column(length = 20)
    private String registrationNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransportRegistrationStatus status;

    @Column(length = 20)
    private String identityNumber;
    @Column(length = 20)
    private String identityIssuer;
    private LocalDate identityIssuedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_term_id")
    private CustomerTerm customerTerm;

    @Column(length = 60)
    private String motherName;
    @Column(length = 60)
    private String fatherName;

    @Column(length = 512)
    private String photoStorageKey;

    private UUID declaredBy;

    private Instant firstApprovedAt;
    private Instant lastApprovedAt;

    // controle das notificações/lembretes de vencimento e de carência
    private Integer lastReminderStage;

    // responsavel por controlar o envio de aviso de bloqueio ao admin apenas uma vez
    private Instant adminBlockWarningSentAt;

    @Enumerated(EnumType.STRING)
    private RegistrationClosedReason closedReason;
    private Instant closedAt;

    private Instant inactiveAt;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    public TransportRegistration() {
    }

    public TransportRegistration(UUID id, UUID studentId, UUID driverId, String registrationNumber, TransportRegistrationStatus status, String identityNumber, String identityIssuer, LocalDate identityIssuedAt, CustomerTerm customerTerm, String motherName, String fatherName, String photoStorageKey, UUID declaredBy, Instant firstApprovedAt, Instant lastApprovedAt, Integer lastReminderStage, Instant adminBlockWarningSentAt, RegistrationClosedReason closedReason, Instant closedAt, Instant inactiveAt, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.studentId = studentId;
        this.driverId = driverId;
        this.registrationNumber = registrationNumber;
        this.status = status;
        this.identityNumber = identityNumber;
        this.identityIssuer = identityIssuer;
        this.identityIssuedAt = identityIssuedAt;
        this.customerTerm = customerTerm;
        this.motherName = motherName;
        this.fatherName = fatherName;
        this.photoStorageKey = photoStorageKey;
        this.declaredBy = declaredBy;
        this.firstApprovedAt = firstApprovedAt;
        this.lastApprovedAt = lastApprovedAt;
        this.lastReminderStage = lastReminderStage;
        this.adminBlockWarningSentAt = adminBlockWarningSentAt;
        this.closedReason = closedReason;
        this.closedAt = closedAt;
        this.inactiveAt = inactiveAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
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

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public TransportRegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(TransportRegistrationStatus status) {
        this.status = status;
    }

    public String getIdentityNumber() {
        return identityNumber;
    }

    public void setIdentityNumber(String identityNumber) {
        this.identityNumber = identityNumber;
    }

    public String getIdentityIssuer() {
        return identityIssuer;
    }

    public void setIdentityIssuer(String identityIssuer) {
        this.identityIssuer = identityIssuer;
    }

    public LocalDate getIdentityIssuedAt() {
        return identityIssuedAt;
    }

    public void setIdentityIssuedAt(LocalDate identityIssuedAt) {
        this.identityIssuedAt = identityIssuedAt;
    }

    public CustomerTerm getCustomerTerm() {
        return customerTerm;
    }

    public void setCustomerTerm(CustomerTerm customerTerm) {
        this.customerTerm = customerTerm;
    }

    public String getMotherName() {
        return motherName;
    }

    public void setMotherName(String motherName) {
        this.motherName = motherName;
    }

    public String getFatherName() {
        return fatherName;
    }

    public void setFatherName(String fatherName) {
        this.fatherName = fatherName;
    }

    public String getPhotoStorageKey() {
        return photoStorageKey;
    }

    public void setPhotoStorageKey(String photoStorageKey) {
        this.photoStorageKey = photoStorageKey;
    }

    public UUID getDeclaredBy() {
        return declaredBy;
    }

    public void setDeclaredBy(UUID declaredBy) {
        this.declaredBy = declaredBy;
    }

    public Instant getFirstApprovedAt() {
        return firstApprovedAt;
    }

    public void setFirstApprovedAt(Instant firstApprovedAt) {
        this.firstApprovedAt = firstApprovedAt;
    }

    public Instant getLastApprovedAt() {
        return lastApprovedAt;
    }

    public void setLastApprovedAt(Instant lastApprovedAt) {
        this.lastApprovedAt = lastApprovedAt;
    }

    public Integer getLastReminderStage() {
        return lastReminderStage;
    }

    public void setLastReminderStage(Integer lastReminderStage) {
        this.lastReminderStage = lastReminderStage;
    }

    public Instant getAdminBlockWarningSentAt() {
        return adminBlockWarningSentAt;
    }

    public void setAdminBlockWarningSentAt(Instant adminBlockWarningSentAt) {
        this.adminBlockWarningSentAt = adminBlockWarningSentAt;
    }

    public RegistrationClosedReason getClosedReason() {
        return closedReason;
    }

    public void setClosedReason(RegistrationClosedReason closedReason) {
        this.closedReason = closedReason;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Instant getInactiveAt() {
        return inactiveAt;
    }

    public void setInactiveAt(Instant inactiveAt) {
        this.inactiveAt = inactiveAt;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
