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
    @ManyToMany
    @JoinTable(
            name = "route_stop_point",
            joinColumns = @JoinColumn(name = "student_id"),
            inverseJoinColumns = @JoinColumn(name = "route_stop_id")
    )
    private Set<RouteStop> routeStops = new HashSet<>();

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
}
