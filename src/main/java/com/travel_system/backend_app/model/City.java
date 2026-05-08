package com.travel_system.backend_app.model;

import com.travel_system.backend_app.model.enums.CitySize;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "CITY_TABLE")
public class City {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    @Enumerated(EnumType.STRING)
    private CitySize size;
    private boolean isActive = true;

    public City() {
    }

    public City(UUID id, String name, CitySize size, boolean isActive) {
        this.id = id;
        this.name = name;
        this.size = size;
        this.isActive = isActive;
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

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}
