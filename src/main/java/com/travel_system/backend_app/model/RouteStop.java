package com.travel_system.backend_app.model;

import com.travel_system.backend_app.model.enums.GeneralStatus;
import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "route_stop")
public class RouteStop {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    private String description;
    @ManyToMany(mappedBy = "routeStops")
    private Set<Student> students = new HashSet<>();
    private Double latitude;
    private Double longitude;
    @ManyToOne
    @JoinTable(
            name = "route_stop_point",
            joinColumns = @JoinColumn(name = "route_stop_id"),
            inverseJoinColumns = @JoinColumn(name = "customer_id")
    )
    private Customer customer;
    @OneToMany(mappedBy = "routeStop")
    private List<RouteStopAssignment> routeStopAssignment = new ArrayList<>();
    @Enumerated(EnumType.STRING)
    private GeneralStatus status;

    public RouteStop() {
    }

    public RouteStop(UUID id, String name, String description, Double latitude, Double longitude, Customer customer, GeneralStatus status) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
        this.customer = customer;
        this.status = status;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<Student> getStudents() {
        return students;
    }

    public void setStudents(Set<Student> students) {
        this.students = students;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public List<RouteStopAssignment> getRouteStopAssignment() {
        return routeStopAssignment;
    }

    public void setRouteStopAssignment(List<RouteStopAssignment> routeStopAssignment) {
        this.routeStopAssignment = routeStopAssignment;
    }

    public GeneralStatus getStatus() {
        return status;
    }

    public void setStatus(GeneralStatus status) {
        this.status = status;
    }
}
