package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.model.enums.InstitutionType;

import javax.validation.constraints.Size;
import java.util.Set;

public record InstitutionUpdateDTO(

        @Size(min = 4, max = 50, message = "O nome da instituição deve ter entre 4 e 50 caracteres")
        String institutionName,

        InstitutionType institutionType
) {
}
