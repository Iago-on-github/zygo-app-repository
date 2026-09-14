package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.ResponsibleAdultType;
import com.travel_system.backend_app.model.enums.StudentRelationshipType;
import com.travel_system.backend_app.model.enums.UserAccountType;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "responsible_adult_table")
@EntityListeners(AuditingEntityListener.class)
public class ResponsibleAdult extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @OneToOne(optional = false)
    @JoinColumn(name = "user_account_id", nullable = false, unique = true)
    private UserAccount userAccount;
    private String name;
    private String lastName;
    @OneToMany(mappedBy = "responsibleAdult")
    private Set<Student> students = new HashSet<>();
    @Column(unique = true)
    private String cpf;
    private String telephone;
    @Enumerated(EnumType.STRING)
    private ResponsibleAdultType responsibleAdultType;
    private String profilePicture;
    @DateTimeFormat(pattern = "dd/MM/yyyy")
    private LocalDate birthdate;
    @Enumerated(EnumType.STRING)
    private GeneralStatus status = GeneralStatus.ACTIVE;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public ResponsibleAdult() {
    }

    public ResponsibleAdult(UUID id, UserAccount userAccount, String name, String lastName, String cpf, String telephone, ResponsibleAdultType responsibleAdultType, String profilePicture, LocalDate birthdate, GeneralStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userAccount = userAccount;
        this.name = name;
        this.lastName = lastName;
        this.cpf = cpf;
        this.telephone = telephone;
        this.responsibleAdultType = responsibleAdultType;
        this.profilePicture = profilePicture;
        this.birthdate = birthdate;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void addStudent(Student student) {
        this.students.add(student);
        student.setResponsibleAdult(this);
    }

    public void removeStudent(Student student) {
        this.students.remove(student);
        student.setResponsibleAdult(null);
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

    public Set<Student> getStudents() {
        return students;
    }

    public void setStudents(Set<Student> students) {
        this.students = students;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public ResponsibleAdultType getResponsibleAdultType() {
        return responsibleAdultType;
    }

    public void setResponsibleAdultType(ResponsibleAdultType responsibleAdultType) {
        this.responsibleAdultType = responsibleAdultType;
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

    public GeneralStatus getStatus() {
        return status;
    }

    public void setStatus(GeneralStatus status) {
        this.status = status;
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
