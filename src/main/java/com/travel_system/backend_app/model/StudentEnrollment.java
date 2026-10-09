package com.travel_system.backend_app.model;

import com.travel_system.backend_app.infrastructure.BaseTenantEntity;
import com.travel_system.backend_app.model.enums.GeneralStatus;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_enrollment_table")
@EntityListeners(AuditingEntityListener.class)
public class StudentEnrollment extends BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(length = 100, nullable = false)
    private String poolOfEnrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_course_id", nullable = false)
    private InstitutionCourse course;

    @Enumerated(EnumType.STRING)
    private GeneralStatus status = GeneralStatus.ACTIVE;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public StudentEnrollment() {
    }

    public StudentEnrollment(UUID id, Student student, String poolOfEnrollment, InstitutionCourse course, GeneralStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.student = student;
        this.poolOfEnrollment = poolOfEnrollment;
        this.course = course;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public String getPoolOfEnrollment() {
        return poolOfEnrollment;
    }

    public void setPoolOfEnrollment(String poolOfEnrollment) {
        this.poolOfEnrollment = poolOfEnrollment;
    }

    public InstitutionCourse getCourse() {
        return course;
    }

    public void setCourse(InstitutionCourse course) {
        this.course = course;
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

/*
* representa a matrícula do aluno em um curso específico
* -> por mais que haja a possibilidade do aluno cursar 2 cursos em uma mesma instituição, a matrícula é diferente
* */
