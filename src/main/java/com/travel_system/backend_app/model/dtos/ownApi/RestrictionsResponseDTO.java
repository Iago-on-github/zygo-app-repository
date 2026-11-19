package com.travel_system.backend_app.model.dtos.ownApi;

import com.travel_system.backend_app.model.enums.RestrictionLevel;

import java.util.List;
import java.util.Set;

public record RestrictionsResponseDTO(
        RestrictionLevel level,
        List<AccessRestriction> restrictions,
        Set<AccessScope> blockedScopes

) {
}
