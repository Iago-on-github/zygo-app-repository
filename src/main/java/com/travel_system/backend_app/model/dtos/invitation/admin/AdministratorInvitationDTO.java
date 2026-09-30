package com.travel_system.backend_app.model.dtos.invitation.admin;

import com.travel_system.backend_app.interfaces.InvitationProfileData;
import com.travel_system.backend_app.model.enums.TargetUserType;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public record AdministratorInvitationDTO(
        @NotBlank(message = "O seu cargo de responsável é obrigatório")
        @Size(min = 4, max = 20, message = "O seu cargo de responsável deve ter entre 4 e 20 caracteres")
        String jobTitle

) implements InvitationProfileData {
    @Override
    public TargetUserType targetUserType() {
        return TargetUserType.ADMINISTRATOR;
    }
}
