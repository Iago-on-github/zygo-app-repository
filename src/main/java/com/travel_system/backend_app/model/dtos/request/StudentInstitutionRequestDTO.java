package com.travel_system.backend_app.model.dtos.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record StudentInstitutionRequestDTO(
        @NotNull
        UUID institutionId,

        @NotEmpty
        Set<UUID> courseIds,

        @NotBlank
        @Size(max = 100, message = "O número da sua matrícula deve ter até 100 caracteres")
        String poolOfEnrollment
) {
}
