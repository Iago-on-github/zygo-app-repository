package com.travel_system.backend_app.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.Shift;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "driver_table")
@EntityListeners(AuditingEntityListener.class)
public class Driver extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @OneToOne(optional = false)
    @JoinColumn(name = "user_account_id", nullable = false, unique = true)
    private UserAccount userAccount;
    private String name;
    private String lastName;
    private String telephone;
    private String profilePicture;
    @DateTimeFormat(pattern = "dd/MM/yyyy")
    private LocalDate birthdate;
    @ElementCollection
    @CollectionTable(name = "driver_shifts", joinColumns = @JoinColumn(name = "driver_id"))
    @Enumerated(EnumType.STRING)
    private Set<Shift> driverShifts = new HashSet<>();
    @Enumerated(EnumType.STRING)
    private GeneralStatus status = GeneralStatus.ACTIVE;
    private String areaOfActivity;
    private Integer totalTrips;
    @OneToMany(mappedBy = "driver")
    private List<Travel> travels = new ArrayList<>();
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

    public Driver() {
    }

    public Driver(UUID id, UserAccount userAccount, String name, String lastName, String telephone, String profilePicture, LocalDate birthdate, GeneralStatus status, String areaOfActivity, Integer totalTrips, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userAccount = userAccount;
        this.name = name;
        this.lastName = lastName;
        this.telephone = telephone;
        this.profilePicture = profilePicture;
        this.birthdate = birthdate;
        this.status = status;
        this.areaOfActivity = areaOfActivity;
        this.totalTrips = totalTrips;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @JsonIgnore
    public List<Travel> getTravels() {
        return travels;
    }

    public void setTravels(List<Travel> travels) {
        this.travels = travels;
    }

    public Integer getTotalTrips() {
        return totalTrips;
    }

    public void setTotalTrips(Integer totalTrips) {
        this.totalTrips = totalTrips;
    }

    public String getAreaOfActivity() {
        return areaOfActivity;
    }

    public void setAreaOfActivity(String areaOfActivity) {
        this.areaOfActivity = areaOfActivity;
    }

    public GeneralStatus getStatus() {
        return status;
    }

    public void setStatus(GeneralStatus status) {
        this.status = status;
    }

    public Set<Shift> getDriverShifts() {
        return driverShifts;
    }

    public void setDriverShifts(Set<Shift> driverShifts) {
        this.driverShifts = driverShifts;
    }

    public LocalDate getBirthdate() {
        return birthdate;
    }

    public void setBirthdate(LocalDate birthdate) {
        this.birthdate = birthdate;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public void setUserAccount(UserAccount userAccount) {
        this.userAccount = userAccount;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
