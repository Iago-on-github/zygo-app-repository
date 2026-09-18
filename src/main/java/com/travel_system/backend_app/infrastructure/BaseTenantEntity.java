package com.travel_system.backend_app.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(name = "customerId", type = UUID.class)
)
@Filter(name = "tenantFilter", condition = "customer_id = :customerId AND customer_id IS NOT NULL")
@MappedSuperclass // modelo de mapeamento para subclasses
public class BaseTenantEntity {

    @Column(name = "customer_id")
    private UUID customerId;

    public UUID getCustomerId() {
        return customerId;
    }

    // setter não pode ser público
    protected void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    @PrePersist
    protected void prePersist() {

        if (this.customerId == null) {
            UUID currentTenant = TenantContext.getCurrentTenant();
            System.out.println("prePersist, currentTenant: " + currentTenant);
            if (currentTenant != null) {
                this.customerId = currentTenant;
            }
        }
    }
}

/*
* GUIDE
* customerID pode ser null no primerio momento = usuário cria a conta, mas não pertence a nenhum customer
* */
