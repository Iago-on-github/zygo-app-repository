package com.travel_system.backend_app.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.travel_system.backend_app.model.enums.CitySize;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "city_table")
public class City {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    @Enumerated(EnumType.STRING)
    private CitySize size;
    @Enumerated(EnumType.STRING)
    private GeneralStatus status = GeneralStatus.ACTIVE;
    @OneToMany(mappedBy = "city")
    private Set<Customer> customers = new HashSet<>();
    @CreatedDate
    private LocalDate createdAt;
    @LastModifiedDate
    private LocalDate updatedAt;

    public City() {
    }

    public City(LocalDate updatedAt, LocalDate createdAt, GeneralStatus status, CitySize size, String name, UUID id) {
        this.updatedAt = updatedAt;
        this.createdAt = createdAt;
        this.status = status;
        this.size = size;
        this.name = name;
        this.id = id;
    }

    public void addCustomer(Customer customer) {
        this.customers.add(customer);
        customer.setCity(this);
    }

    public void removeCustomer(Customer customer) {
        this.customers.remove(customer);
        customer.setCity(null);
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

    public CitySize getSize() {
        return size;
    }

    public void setSize(CitySize size) {
        this.size = size;
    }

    public GeneralStatus getStatus() {
        return status;
    }

    public void setStatus(GeneralStatus status) {
        this.status = status;
    }

    @JsonIgnore
    public Set<Customer> getCustomers() {
        return customers;
    }

    public void setCustomers(Set<Customer> customers) {
        this.customers = customers;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDate getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDate updatedAt) {
        this.updatedAt = updatedAt;
    }
}
