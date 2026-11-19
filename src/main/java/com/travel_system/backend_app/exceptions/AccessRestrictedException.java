package com.travel_system.backend_app.exceptions;

import com.travel_system.backend_app.model.dtos.ownApi.AccessRestriction;
import com.travel_system.backend_app.model.enums.AccessCategory;
import com.travel_system.backend_app.model.enums.RestrictionLevel;

import java.util.List;

public class AccessRestrictedException extends RuntimeException {

    private final AccessCategory accessCategory;
    private final RestrictionLevel restrictionLevel;
    private final List<AccessRestriction> restrictions;

    public AccessRestrictedException(String msg, AccessCategory accessCategory, RestrictionLevel restrictionLevel, List<AccessRestriction> restrictions) {
        super(msg);
        this.accessCategory = accessCategory;

        this.restrictionLevel = restrictionLevel;
        this.restrictions = restrictions;
    }

    public AccessCategory getAccessCategory() {
        return accessCategory;
    }

    public RestrictionLevel getRestrictionLevel() {
        return restrictionLevel;
    }

    public List<AccessRestriction> getRestrictions() {
        return restrictions;
    }
}
