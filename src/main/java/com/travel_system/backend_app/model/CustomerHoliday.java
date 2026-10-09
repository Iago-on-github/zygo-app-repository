package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.Shift;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "customer_holiday_table")
public class CustomerHoliday extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private LocalDate date;
    @Column(nullable = false)
    private String description;
    @Column(nullable = false)
    private UUID createdBy;

    @ElementCollection
    @CollectionTable(name = "shifts_customer_holiday", joinColumns = @JoinColumn(name = "customer_holiday_id"))
    @Enumerated(EnumType.STRING)
    private Set<Shift> shifts = new HashSet<>();

    public CustomerHoliday() {
    }

    public CustomerHoliday(UUID id, LocalDate date, String description, UUID createdBy) {
        this.id = id;
        this.date = date;
        this.description = description;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<Shift> getShifts() {
        return shifts;
    }

    public void setShifts(Set<Shift> shifts) {
        this.shifts = shifts;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}
