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
        parameters = @ParamDef(name = "customerId", type = UUID.class),
        applyToLoadByKey = true
)
@Filter(name = "tenantFilter", condition = "customer_id = :customerId")
@MappedSuperclass // modelo de mapeamento para subclasses
public class BaseTenantEntity {

    // toda entidade de domínio pertence obrigatoriamente a um Customer, e ele nunca muda após o insert
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    public UUID getCustomerId() {
        return customerId;
    }

    // vincula a entidade a um Customer de forma explícita (ex.: aceite de convite, sem TenantContext);
    // permite apenas uma atribuição: nunca move a entidade para outro Customer
    public void assignCustomer(UUID customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId não pode ser nulo");
        }
        if (this.customerId != null && !this.customerId.equals(customerId)) {
            throw new IllegalStateException("A entidade já pertence a outro Customer");
        }
        this.customerId = customerId;
    }

    // fluxo padrão: sem atribuição explícita, usa o Customer do TenantContext;
    // se nenhum dos dois existir, bloqueia a persistência
    @PrePersist
    protected void prePersist() {
        if (this.customerId == null) {
            UUID currentTenant = TenantContext.getCurrentTenant();
            if (currentTenant == null) {
                throw new IllegalStateException("Entidade de tenant sem Customer definido");
            }
            this.customerId = currentTenant;
        }
    }
}

/*
* GUIDE
* customerID pode ser null no primerio momento = usuário cria a conta, mas não pertence a nenhum customer
* */
