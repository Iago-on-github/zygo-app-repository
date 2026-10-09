package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customer_term_table")
public class CustomerTerm extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private LocalDate startTerm;
    @Column(nullable = false)
    private LocalDate endTerm;

    public CustomerTerm() {
    }

    public CustomerTerm(UUID id, String name, LocalDate startTerm, LocalDate endTerm) {
        this.id = id;
        this.name = name;
        this.startTerm = startTerm;
        this.endTerm = endTerm;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getStartTerm() {
        return startTerm;
    }

    public void setStartTerm(LocalDate startTerm) {
        this.startTerm = startTerm;
    }

    public LocalDate getEndTerm() {
        return endTerm;
    }

    public void setEndTerm(LocalDate endTerm) {
        this.endTerm = endTerm;
    }
}
