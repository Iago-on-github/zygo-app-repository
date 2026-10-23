package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import jakarta.persistence.*;

import java.util.UUID;

/*
* representa a numeração de registro (matrícula)
* */

@Entity
@Table(name = "registration_number_sequence_table")
public class RegistrationNumberSequence{

    // usa customer id como primarykey
    @Id
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "last_value", nullable = false)
    private Long lastValue = 0L;

    public RegistrationNumberSequence() {
    }

    public RegistrationNumberSequence(UUID customerId, Long lastValue) {
        this.customerId = customerId;
        this.lastValue = lastValue;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public Long getLastValue() {
        return lastValue;
    }

    public void setLastValue(Long lastValue) {
        this.lastValue = lastValue;
    }
}

