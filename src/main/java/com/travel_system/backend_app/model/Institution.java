package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import com.travel_system.backend_app.model.enums.InstitutionType;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@BatchSize(size = 25)
@Entity
@Table(name = "institution_table")
@EntityListeners(AuditingEntityListener.class)
public class Institution extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(length = 50)
    private String institutionName;
    @Enumerated(EnumType.STRING)
    private InstitutionType institutionType;
    @OneToMany(mappedBy = "institution", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<InstitutionCourse> courses = new HashSet<>();
    @Enumerated(EnumType.STRING)
    private GeneralStatus status = GeneralStatus.ACTIVE;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public Institution() {
    }

    public Institution(Instant updatedAt, Instant createdAt, GeneralStatus status, InstitutionType institutionType, String institutionName, UUID id) {
        this.updatedAt = updatedAt;
        this.createdAt = createdAt;
        this.status = status;
        this.institutionType = institutionType;
        this.institutionName = institutionName;
        this.id = id;
    }

    /*
    * atualiza ambos os lados do relacionamento bidirecional
    * */
    public InstitutionCourse addCourse(String course) {
        InstitutionCourse institutionCourse = new InstitutionCourse();

        institutionCourse.setInstitution(this);
        institutionCourse.setName(course);

        this.courses.add(institutionCourse);

        return institutionCourse;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public void setInstitutionName(String institutionName) {
        this.institutionName = institutionName;
    }

    public InstitutionType getInstitutionType() {
        return institutionType;
    }

    public void setInstitutionType(InstitutionType institutionType) {
        this.institutionType = institutionType;
    }

    public Set<InstitutionCourse> getCourses() {
        return courses;
    }

    public void setCourses(Set<InstitutionCourse> courses) {
        this.courses = courses;
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
