package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.Action;
import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.model.enums.ResourceType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sensitive_access_log_table")
public class SensitiveDataAccessLog extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID actorAccountId;
    @Enumerated(EnumType.STRING)
    private OwnerType ownerType;
    @Column(nullable = false)
    private UUID ownerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceType resourceType;
    @Column(nullable = false)
    private UUID resourceId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;
    private UUID contextId;
    private Instant accessAt;

    public SensitiveDataAccessLog() {
    }

    public SensitiveDataAccessLog(UUID id, UUID actorAccountId, OwnerType ownerType, UUID ownerId, ResourceType resourceType, UUID resourceId, Action action, UUID contextId, Instant accessAt) {
        this.id = id;
        this.actorAccountId = actorAccountId;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.action = action;
        this.contextId = contextId;
        this.accessAt = accessAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getActorAccountId() {
        return actorAccountId;
    }

    public void setActorAccountId(UUID actorAccountId) {
        this.actorAccountId = actorAccountId;
    }

    public OwnerType getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(OwnerType ownerType) {
        this.ownerType = ownerType;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public UUID getContextId() {
        return contextId;
    }

    public void setContextId(UUID contextId) {
        this.contextId = contextId;
    }

    public Instant getAccessAt() {
        return accessAt;
    }

    public void setAccessAt(Instant accessAt) {
        this.accessAt = accessAt;
    }
}

/*
* entidade de auditoria para registrar quem acessou os dados (sensíveis) de quem, quando e como
*
* guide:
* contextId: id da viagem (anulável, pode registrar fora da viagem)
*
* */
