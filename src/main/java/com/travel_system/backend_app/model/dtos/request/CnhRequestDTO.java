package com.travel_system.backend_app.model.dtos.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.travel_system.backend_app.model.enums.CnhCategory;
import org.springframework.cglib.core.Local;

import javax.validation.constraints.Future;
import javax.validation.constraints.Past;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;

public record CnhRequestDTO(
        @Size(min = 11, max = 11, message = "A numeração da sua CNH deve conter no maximo 11 digitos")
        String cnhNumber,

        Set<CnhCategory> cnhCategories,

        @Future(message = "A data de expiração da sua carteira deve estar no futuro")
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate cnhExpirationDate,

        @Past(message = "A data da primeira emissão deve estar no passado")
        @JsonFormat(pattern = "dd/MM/yyyy")
        LocalDate cnhFirstIssueDate
) {
}
