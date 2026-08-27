package com.travel_system.backend_app.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

@MappedSuperclass // modelo de mapeamento para subclasses
public class BaseTenantEntity {

    @TenantId
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }
}
