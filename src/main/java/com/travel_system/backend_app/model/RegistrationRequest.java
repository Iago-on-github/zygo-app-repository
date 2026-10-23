package com.travel_system.backend_app.model;

/*
* representa cada solicitação enviada para análise
* */

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.RegistrationRequestStatus;
import com.travel_system.backend_app.model.enums.RegistrationRequestType;
import com.travel_system.backend_app.model.enums.Shift;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "registration_request_table")
@EntityListeners(AuditingEntityListener.class)
public class RegistrationRequest extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_registration_id", nullable = false)
    private TransportRegistration transportRegistration;

    @Enumerated(EnumType.STRING)
    private RegistrationRequestType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationRequestStatus status;

    // período que a solicitação vai cobrir (resolvido na aprovação)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_term_id")
    private CustomerTerm targetTerm;

    @Column(length = 20)
    private String identityNumber;
    @Column(length = 20)
    private String identityIssuer;
    private LocalDate identityIssuedAt;

    @Column(length = 60)
    private String motherName;
    @Column(length = 60)
    private String fatherName;

    /*
    * apenas para alunos
    * - copia da instituição, curso e turnos
    * */
    private UUID institutionCourseId;
    private String poolOfEnrollment;
    @ElementCollection
    @CollectionTable(name = "registration_request_shifts",
        joinColumns = @JoinColumn(name = "registration_request_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "shift", nullable = false)
    private Set<Shift> shifts = new HashSet<>();

    @Column(length = 512)
    private String pendingPhotoStorageKey;

    // quem enviou e quando
    private UUID submittedBy;
    private Instant submittedAt;

    // quem revisou
    private UUID reviewedBy;
    private Instant reviewedAt;
    @Column(length = 500)
    private String rejectionReason;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    public RegistrationRequest() {
    }

    public RegistrationRequest(UUID id, TransportRegistration transportRegistration, RegistrationRequestType type, RegistrationRequestStatus status, CustomerTerm targetTerm, String identityNumber, String identityIssuer, LocalDate identityIssuedAt, String motherName, String fatherName, UUID institutionCourseId, String poolOfEnrollment, Set<Shift> shifts, String pendingPhotoStorageKey, UUID submittedBy, Instant submittedAt, UUID reviewedBy, Instant reviewedAt, String rejectionReason, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.transportRegistration = transportRegistration;
        this.type = type;
        this.status = status;
        this.targetTerm = targetTerm;
        this.identityNumber = identityNumber;
        this.identityIssuer = identityIssuer;
        this.identityIssuedAt = identityIssuedAt;
        this.motherName = motherName;
        this.fatherName = fatherName;
        this.institutionCourseId = institutionCourseId;
        this.poolOfEnrollment = poolOfEnrollment;
        this.shifts = shifts;
        this.pendingPhotoStorageKey = pendingPhotoStorageKey;
        this.submittedBy = submittedBy;
        this.submittedAt = submittedAt;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.rejectionReason = rejectionReason;
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

    public TransportRegistration getTransportRegistration() {
        return transportRegistration;
    }

    public void setTransportRegistration(TransportRegistration transportRegistration) {
        this.transportRegistration = transportRegistration;
    }

    public RegistrationRequestType getType() {
        return type;
    }

    public void setType(RegistrationRequestType type) {
        this.type = type;
    }

    public RegistrationRequestStatus getStatus() {
        return status;
    }

    public void setStatus(RegistrationRequestStatus status) {
        this.status = status;
    }

    public CustomerTerm getTargetTerm() {
        return targetTerm;
    }

    public void setTargetTerm(CustomerTerm targetTerm) {
        this.targetTerm = targetTerm;
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

    public UUID getInstitutionCourseId() {
        return institutionCourseId;
    }

    public void setInstitutionCourseId(UUID institutionCourseId) {
        this.institutionCourseId = institutionCourseId;
    }

    public String getPoolOfEnrollment() {
        return poolOfEnrollment;
    }

    public void setPoolOfEnrollment(String poolOfEnrollment) {
        this.poolOfEnrollment = poolOfEnrollment;
    }

    public Set<Shift> getShifts() {
        return shifts;
    }

    public void setShifts(Set<Shift> shifts) {
        this.shifts = shifts;
    }

    public String getPendingPhotoStorageKey() {
        return pendingPhotoStorageKey;
    }

    public void setPendingPhotoStorageKey(String pendingPhotoStorageKey) {
        this.pendingPhotoStorageKey = pendingPhotoStorageKey;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(UUID submittedBy) {
        this.submittedBy = submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
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

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
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
