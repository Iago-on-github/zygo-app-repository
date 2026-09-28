package com.travel_system.backend_app.model.dtos.invitation;

import com.travel_system.backend_app.model.enums.InstitutionType;
import com.travel_system.backend_app.model.enums.Shift;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Set;

public record StudentInvitationDTO(
        @NotEmpty
        Set<Shift> studentShifts,
        @NotNull
        InstitutionType institutionType,
        @NotNull
        String course
) {
}
// dados operacionais do estudante ao entrar no customer p/ usar com o profileData
