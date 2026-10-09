package com.travel_system.backend_app.model.dtos.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public record InstitutionCourseRequestDTO(
        @NotBlank
        @Size(max = 50, message = "O nome do seu curso deve ter ao máximo 50 caracteres")
        String courseName
) {
}
