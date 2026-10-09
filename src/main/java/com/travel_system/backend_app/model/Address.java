package com.travel_system.backend_app.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "address_table")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(length = 150, nullable = false)
    private String street;
    private Integer number;
    @Column(length = 50, nullable = false)
    private String neighborhood;
    @Column(length = 50, nullable = false)
    private String city;
    @Column(length = 8, nullable = false)
    private String cep;
    @Column(length = 100)
    private String complement;

    public Address() {
    }

    public Address(UUID id, String street, Integer number, String neighborhood, String city, String cep, String complement) {
        this.id = id;
        this.street = street;
        this.number = number;
        this.neighborhood = neighborhood;
        this.city = city;
        this.cep = cep;
        this.complement = complement;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public void setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCep() {
        return cep;
    }

    public void setCep(String cep) {
        this.cep = cep;
    }

    public String getComplement() {
        return complement;
    }

    public void setComplement(String complement) {
        this.complement = complement;
    }
}
