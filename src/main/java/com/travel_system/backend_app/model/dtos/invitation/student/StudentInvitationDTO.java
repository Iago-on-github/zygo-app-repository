package com.travel_system.backend_app.model.dtos.invitation.student;

import com.travel_system.backend_app.interfaces.InvitationProfileData;
import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.model.enums.TargetUserType;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Set;

public record StudentInvitationDTO(
        @NotEmpty
        Set<Shift> studentShifts,
        @NotEmpty
        InstitutionType institutionType,
        @NotEmpty
        @Size(min = 4, max = 15, message = "o nome do seu curso deve ter entre 4 e 15 caracteres")
        String course
) implements InvitationProfileData {

        @Override
        public TargetUserType targetUserType() {
           return TargetUserType.STUDENT;
        }
}

// dados operacionais do estudante ao entrar no customer p/ usar com o profileData
