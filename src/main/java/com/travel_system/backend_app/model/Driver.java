package com.travel_system.backend_app.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "DRIVER_TABLE")
@PrimaryKeyJoinColumn(name = "id")
public class Driver extends UserModel {
    private String areaOfActivity;
    private Integer totalTrips;
    @OneToMany(mappedBy = "driver")
    private List<Travel> travels = new ArrayList<>();
    @ManyToOne
    @JoinColumn(name = "city_id")
    private City city;

    public Driver() {
    }

    public Driver(UUID id, String email, String password, String name, String lastName, String telephone, String profilePicture, GeneralStatus status, LocalDateTime createdAt, LocalDateTime updatedAt, String areaOfActivity, Integer totalTrips, List<Travel> travels, City city) {
        super(id, email, password, name, lastName, telephone, profilePicture, status, createdAt, updatedAt);
        this.areaOfActivity = areaOfActivity;
        this.totalTrips = totalTrips;
        this.travels = travels;
        this.city = city;
    }

    public String getAreaOfActivity() {
        return areaOfActivity;
    }

    public void setAreaOfActivity(String areaOfActivity) {
        this.areaOfActivity = areaOfActivity;
    }

    public Integer getTotalTrips() {
        return totalTrips;
    }

    public void setTotalTrips(Integer totalTrips) {
        this.totalTrips = totalTrips;
    }

    @JsonIgnore
    public List<Travel> getTravels() {
        return travels;
    }

    public void setTravels(List<Travel> travels) {
        this.travels = travels;
    }

    public City getCity() {
        return city;
    }

    public void setCity(City city) {
        this.city = city;
    }
}
