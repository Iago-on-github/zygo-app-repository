package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.model.enums.StudentRelationshipType;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "student_table")
@EntityListeners(AuditingEntityListener.class)
public class Student extends BaseTenantEntity {

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
    @CollectionTable(name = "student_shifts", joinColumns = @JoinColumn(name = "student_id"))
    @Enumerated(EnumType.STRING)
    private Set<Shift> studentShift = new HashSet<>();
    @Enumerated(EnumType.STRING)
    private GeneralStatus status = GeneralStatus.ACTIVE;
    @Enumerated(EnumType.STRING)
    private InstitutionType institutionType;
    private String course;
    @Enumerated(EnumType.STRING)
    private StudentRelationshipType studentRelationshipType;
    @OneToMany(mappedBy = "student")
    private Set<StudentTravel> studentTravels = new HashSet<>();
    @OneToMany(mappedBy = "student")
    private List<StudentRouteStopAssignment> studentRouteStopAssignments = new ArrayList<>();
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_adult_id")
    private ResponsibleAdult responsibleAdult;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public Student() {
    }

    public Student(UUID id, UserAccount userAccount, String name, String lastName, String telephone, String profilePicture, LocalDate birthdate, GeneralStatus status, InstitutionType institutionType, String course, ResponsibleAdult responsibleAdult, Instant createdAt, Instant updatedAt, StudentRelationshipType studentRelationshipType) {
        this.id = id;
        this.userAccount = userAccount;
        this.name = name;
        this.lastName = lastName;
        this.telephone = telephone;
        this.profilePicture = profilePicture;
        this.birthdate = birthdate;
        this.status = status;
        this.institutionType = institutionType;
        this.course = course;
        this.responsibleAdult = responsibleAdult;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.studentRelationshipType = studentRelationshipType;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public void setUserAccount(UserAccount userAccount) {
        this.userAccount = userAccount;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }

    public LocalDate getBirthdate() {
        return birthdate;
    }

    public void setBirthdate(LocalDate birthdate) {
        this.birthdate = birthdate;
    }

    public Set<Shift> getStudentShift() {
        return studentShift;
    }

    public void setStudentShift(Set<Shift> studentShift) {
        this.studentShift = studentShift;
    }

    public GeneralStatus getStatus() {
        return status;
    }

    public void setStatus(GeneralStatus status) {
        this.status = status;
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

    public StudentRelationshipType getStudentRelationshipType() {
        return studentRelationshipType;
    }

    public void setStudentRelationshipType(StudentRelationshipType studentRelationshipType) {
        this.studentRelationshipType = studentRelationshipType;
    }

    public Set<StudentTravel> getStudentTravels() {
        return studentTravels;
    }

    public void setStudentTravels(Set<StudentTravel> studentTravels) {
        this.studentTravels = studentTravels;
    }

    public List<StudentRouteStopAssignment> getStudentRouteStopAssignments() {
        return studentRouteStopAssignments;
    }

    public void setStudentRouteStopAssignments(List<StudentRouteStopAssignment> studentRouteStopAssignments) {
        this.studentRouteStopAssignments = studentRouteStopAssignments;
    }

    public ResponsibleAdult getResponsibleAdult() {
        return responsibleAdult;
    }

    public void setResponsibleAdult(ResponsibleAdult responsibleAdult) {
        this.responsibleAdult = responsibleAdult;
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
