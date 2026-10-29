package com.travel_system.backend_app.model.dtos.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public record RejectRegistrationDTO(

        @NotBlank
        @Size(max = 500, message = "O motivo da rejeição deve ter até 500 caracteres")
        String rejectionReason
) {
}
