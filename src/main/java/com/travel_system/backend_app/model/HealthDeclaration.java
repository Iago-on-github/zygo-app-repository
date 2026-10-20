package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.HealthDeclarationStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static jakarta.persistence.CascadeType.ALL;

@Entity
@Table(name = "health_declaration_table")
public class HealthDeclaration extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID studentId;
    private UUID driverId;
    @Column(nullable = false)
    private boolean hasCondition = false;
    @OneToMany(mappedBy = "declaration", cascade = ALL, orphanRemoval = true)
    private List<HealthCondition> conditions = new ArrayList<>();
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private HealthDeclarationStatus healthDeclarationStatus;
    @ManyToOne(fetch = FetchType.LAZY)
    private CustomerTerm customerTerm;
    @Column(nullable = false)
    private UUID declaredBy;
    @Column(nullable = false)
    private Instant declaredAt;

    public HealthDeclaration() {
    }

    public HealthDeclaration(UUID id, UUID studentId, UUID driverId, boolean hasCondition, List<HealthCondition> conditions, HealthDeclarationStatus healthDeclarationStatus, CustomerTerm customerTerm, UUID declaredBy, Instant declaredAt) {
        this.id = id;
        this.studentId = studentId;
        this.driverId = driverId;
        this.hasCondition = hasCondition;
        this.conditions = conditions;
        this.healthDeclarationStatus = healthDeclarationStatus;
        this.customerTerm = customerTerm;
        this.declaredBy = declaredBy;
        this.declaredAt = declaredAt;
    }

    // mantém os dois lados do relacionamento sincronizados (sem isso a FK da condição fica nula)
    public void addCondition(HealthCondition condition) {
        condition.setDeclaration(this);
        this.conditions.add(condition);
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

    public boolean isHasCondition() {
        return hasCondition;
    }

    public void setHasCondition(boolean hasCondition) {
        this.hasCondition = hasCondition;
    }

    public List<HealthCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<HealthCondition> conditions) {
        this.conditions = conditions;
    }

    public HealthDeclarationStatus getHealthDeclarationStatus() {
        return healthDeclarationStatus;
    }

    public void setHealthDeclarationStatus(HealthDeclarationStatus healthDeclarationStatus) {
        this.healthDeclarationStatus = healthDeclarationStatus;
    }

    public CustomerTerm getCustomerTerm() {
        return customerTerm;
    }

    public void setCustomerTerm(CustomerTerm customerTerm) {
        this.customerTerm = customerTerm;
    }

    public UUID getDeclaredBy() {
        return declaredBy;
    }

    public void setDeclaredBy(UUID declaredBy) {
        this.declaredBy = declaredBy;
    }

    public Instant getDeclaredAt() {
        return declaredAt;
    }

    public void setDeclaredAt(Instant declaredAt) {
        this.declaredAt = declaredAt;
    }
}
