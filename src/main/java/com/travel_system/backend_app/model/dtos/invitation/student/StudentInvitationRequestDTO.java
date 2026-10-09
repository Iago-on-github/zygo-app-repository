package com.travel_system.backend_app.model.dtos.invitation.student;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

public record StudentInvitationRequestDTO(
        @Email
        @NotBlank
        String email,

        @NotNull
        @Valid
        StudentInvitationDTO profileData
) {
}
