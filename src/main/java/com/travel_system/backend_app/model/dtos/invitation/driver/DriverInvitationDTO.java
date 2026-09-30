package com.travel_system.backend_app.model.dtos.invitation.driver;

import com.travel_system.backend_app.interfaces.InvitationProfileData;
import com.travel_system.backend_app.model.enums.Shift;
import com.travel_system.backend_app.model.enums.TargetUserType;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Set;

public record DriverInvitationDTO(
        @NotEmpty
        Set<Shift> driverShifts,
        @NotBlank(message = "A área de atuação é obrigatória")
        String areaOfActivity

) implements InvitationProfileData {
    @Override
    public TargetUserType targetUserType() {
        return TargetUserType.DRIVER;
    }
}
