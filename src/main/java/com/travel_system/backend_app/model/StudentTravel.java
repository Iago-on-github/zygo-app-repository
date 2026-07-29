package com.travel_system.backend_app.model;

import com.travel_system.backend_app.model.enums.StudentTravelStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_travel")
public class StudentTravel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne
    @JoinColumn(name = "travel_id")
    private Travel travel;
    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;
    private boolean embark = false;
    private Instant embarkHour;
    private Instant disembarkHour;
    @OneToOne(mappedBy = "studentTravel")
    private GeoPosition position;
    @Enumerated(EnumType.STRING)
    private StudentTravelStatus studentTravelStatus;

    public StudentTravel() {
    }

    public StudentTravel(UUID id, Travel travel, Student student, boolean embark, Instant embarkHour, Instant disembarkHour, GeoPosition position, StudentTravelStatus studentTravelStatus) {
        this.id = id;
        this.travel = travel;
        this.student = student;
        this.embark = embark;
        this.embarkHour = embarkHour;
        this.disembarkHour = disembarkHour;
        this.position = position;
        this.studentTravelStatus = studentTravelStatus;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Travel getTravel() {
        return travel;
    }

    public void setTravel(Travel travel) {
        this.travel = travel;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public boolean isEmbark() {
        return embark;
    }

    public void setEmbark(boolean embark) {
        this.embark = embark;
    }

    public Instant getEmbarkHour() {
        return embarkHour;
    }

    public void setEmbarkHour(Instant embarkHour) {
        this.embarkHour = embarkHour;
    }

    public Instant getDisembarkHour() {
        return disembarkHour;
    }

    public void setDisembarkHour(Instant disembarkHour) {
        this.disembarkHour = disembarkHour;
    }

    public GeoPosition getPosition() {
        return position;
    }

    public void setPosition(GeoPosition position) {
        this.position = position;
    }

    public StudentTravelStatus getStudentTravelStatus() {
        return studentTravelStatus;
    }

    public void setStudentTravelStatus(StudentTravelStatus studentTravelStatus) {
        this.studentTravelStatus = studentTravelStatus;
    }
}
