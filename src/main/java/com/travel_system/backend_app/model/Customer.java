package com.travel_system.backend_app.model;

import com.travel_system.backend_app.model.enums.ClientSector;
import com.travel_system.backend_app.model.enums.CustomerPlan;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.Shift;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Entity
@Table(name = "customer_table")
@EntityListeners(AuditingEntityListener.class)
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    @Column(unique = true)
    private String slug;
    @Column(unique = true, nullable = false)
    private String legalName;
    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;
    private String contactEmail;
    private String contactTelephone;
    @Enumerated(EnumType.STRING)
    private GeneralStatus status = GeneralStatus.ACTIVE;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", referencedColumnName = "id", nullable = false)
    private City city;
    @Enumerated(EnumType.STRING)
    private ClientSector clientSector;
    private String logoUrl;
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "address_id")
    private Address address;

    @Enumerated(EnumType.STRING)
    private CustomerPlan plan = CustomerPlan.LITE;

    // dados informativos
    @ElementCollection
    @CollectionTable(name = "customer_shifts", joinColumns = @JoinColumn(name = "customer_id"))
    @Enumerated(EnumType.STRING)
    private Set<Shift> shifts = new HashSet<>();
    @ElementCollection
    @CollectionTable(name = "customer_operating_days", joinColumns = @JoinColumn(name = "customer_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week")
    private Set<DayOfWeek> operatingDays = new HashSet<>();
    @Column(nullable = false, length = 50)
    private String timeZone = "America/Sao_Paulo";

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public Customer() {
    }

    public Customer(Instant updatedAt, Instant createdAt, String timeZone, CustomerPlan plan, Address address, String logoUrl, ClientSector clientSector, City city, GeneralStatus status, String contactTelephone, String contactEmail, String cnpj, String legalName, String slug, String name, UUID id) {
        this.updatedAt = updatedAt;
        this.createdAt = createdAt;
        this.timeZone = timeZone;
        this.plan = plan;
        this.address = address;
        this.logoUrl = logoUrl;
        this.clientSector = clientSector;
        this.city = city;
        this.status = status;
        this.contactTelephone = contactTelephone;
        this.contactEmail = contactEmail;
        this.cnpj = cnpj;
        this.legalName = legalName;
        this.slug = slug;
        this.name = name;
        this.id = id;
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

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactTelephone() {
        return contactTelephone;
    }

    public void setContactTelephone(String contactTelephone) {
        this.contactTelephone = contactTelephone;
    }

    public GeneralStatus getStatus() {
        return status;
    }

    public void setStatus(GeneralStatus status) {
        this.status = status;
    }

    public City getCity() {
        return city;
    }

    public void setCity(City city) {
        this.city = city;
    }

    public ClientSector getClientSector() {
        return clientSector;
    }

    public void setClientSector(ClientSector clientSector) {
        this.clientSector = clientSector;
    }


    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public CustomerPlan getPlan() {
        return plan;
    }

    public void setPlan(CustomerPlan plan) {
        this.plan = plan;
    }

    public Set<Shift> getShifts() {
        return shifts;
    }

    public void setShifts(Set<Shift> shifts) {
        this.shifts = shifts;
    }

    public Set<DayOfWeek> getOperatingDays() {
        return operatingDays;
    }

    public void setOperatingDays(Set<DayOfWeek> operatingDays) {
        this.operatingDays = operatingDays;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
