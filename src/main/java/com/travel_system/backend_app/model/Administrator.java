package com.travel_system.backend_app.model;

import com.travel_system.backend_app.model.enums.GeneralStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "ADM_TABLE")
public class Administrator extends UserModel {

    private String cpf;
    private String birthDate;

    public Administrator() {
    }

    public Administrator(UUID id, String email, String password, String name, String lastName, String telephone, String profilePicture, GeneralStatus status, LocalDateTime createdAt, LocalDateTime updatedAt, Customer customer, String cpf, String birthDate) {
        super(id, email, password, name, lastName, telephone, profilePicture, status, createdAt, updatedAt, customer);
        this.cpf = cpf;
        this.birthDate = birthDate;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }
}
