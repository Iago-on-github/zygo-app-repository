package com.travel_system.backend_app.model.dtos.invitation.responsible;

import com.travel_system.backend_app.interfaces.InvitationProfileData;
import com.travel_system.backend_app.model.enums.ResponsibleAdultType;
import com.travel_system.backend_app.model.enums.TargetUserType;

import javax.validation.constraints.NotNull;

public record ResponsibleAdultInvitationDTO(
        @NotNull
        ResponsibleAdultType responsibleAdultType
) implements InvitationProfileData {
        @Override
        public TargetUserType targetUserType() {
                return TargetUserType.RESPONSIBLE_ADULT;
        }
}
