package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.HealthConditionSeverity;
import com.travel_system.backend_app.model.enums.HealthConditionType;
import com.travel_system.backend_app.model.enums.HealthDeclarationStatus;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "health_condition_table")
public class HealthCondition extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private HealthDeclaration declaration;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HealthConditionType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HealthConditionSeverity severity;
    @Column(length = 300, nullable = false)
    private String description;
    @Column(length = 500, nullable = false)
    private String emergencyInstructions;
    private String medication;
    private String whereIsMedication;
    private String emergencyContactName;
    private String emergencyContactTelephone;

    public HealthCondition() {
    }

    public HealthCondition(UUID id, HealthDeclaration declaration, HealthConditionType type, HealthConditionSeverity severity, String description, String emergencyInstructions, String medication, String whereIsMedication, String emergencyContactName, String emergencyContactTelephone) {
        this.id = id;
        this.declaration = declaration;
        this.type = type;
        this.severity = severity;
        this.description = description;
        this.emergencyInstructions = emergencyInstructions;
        this.medication = medication;
        this.whereIsMedication = whereIsMedication;
        this.emergencyContactName = emergencyContactName;
        this.emergencyContactTelephone = emergencyContactTelephone;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public HealthDeclaration getDeclaration() {
        return declaration;
    }

    public void setDeclaration(HealthDeclaration declaration) {
        this.declaration = declaration;
    }

    public HealthConditionType getType() {
        return type;
    }

    public void setType(HealthConditionType type) {
        this.type = type;
    }

    public HealthConditionSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(HealthConditionSeverity severity) {
        this.severity = severity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEmergencyInstructions() {
        return emergencyInstructions;
    }

    public void setEmergencyInstructions(String emergencyInstructions) {
        this.emergencyInstructions = emergencyInstructions;
    }

    public String getMedication() {
        return medication;
    }

    public void setMedication(String medication) {
        this.medication = medication;
    }

    public String getWhereIsMedication() {
        return whereIsMedication;
    }

    public void setWhereIsMedication(String whereIsMedication) {
        this.whereIsMedication = whereIsMedication;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public void setEmergencyContactName(String emergencyContactName) {
        this.emergencyContactName = emergencyContactName;
    }

    public String getEmergencyContactTelephone() {
        return emergencyContactTelephone;
    }

    public void setEmergencyContactTelephone(String emergencyContactTelephone) {
        this.emergencyContactTelephone = emergencyContactTelephone;
    }
}
