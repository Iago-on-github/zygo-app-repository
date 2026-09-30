package com.travel_system.backend_app.interfaces;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.travel_system.backend_app.model.enums.TargetUserType;

public interface InvitationProfileData {
    @JsonIgnore
    TargetUserType targetUserType();
}
