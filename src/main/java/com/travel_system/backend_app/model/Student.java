package com.travel_system.backend_app.model;

import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "STUDENT_TABLE")
public class Student extends UserModel {
    @Enumerated(EnumType.STRING)
    private InstitutionType institutionType;
    private String course;
    @OneToMany(mappedBy = "student")
    private Set<StudentTravel> studentTravels = new HashSet<>();
    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL)
    private Set<DeviceToken> deviceTokens = new HashSet<>();

    public Student() {
    }

    public Student(UUID id, String email, String password, String name, String lastName, String telephone, String profilePicture, GeneralStatus status, LocalDateTime createdAt, LocalDateTime updatedAt, Customer customer, InstitutionType institutionType, String course) {
        super(id, email, password, name, lastName, telephone, profilePicture, status, createdAt, updatedAt, customer);
        this.institutionType = institutionType;
        this.course = course;
    }

    public InstitutionType getInstitutionType() {
        return institutionType;
    }

    public void setInstitutionType(InstitutionType institutionType) {
        this.institutionType = institutionType;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    public Set<StudentTravel> getStudentTravels() {
        return studentTravels;
    }

    public void setStudentTravels(Set<StudentTravel> studentTravels) {
        this.studentTravels = studentTravels;
    }

    public Set<DeviceToken> getDeviceTokens() {
        return deviceTokens;
    }

    public void setDeviceTokens(Set<DeviceToken> deviceTokens) {
        this.deviceTokens = deviceTokens;
    }
}
