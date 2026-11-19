package com.travel_system.backend_app.exceptions.standardError;


import com.travel_system.backend_app.model.dtos.ownApi.AccessRestriction;
import com.travel_system.backend_app.model.enums.RestrictionLevel;

import java.time.LocalDate;
import java.util.List;

public class AccessRestrictedError extends StandardError{

    private String errorCode;
    private AccessScope scope;
    private RestrictionLevel level;
    private List<AccessRestriction> restrictions;

    public AccessRestrictedError(LocalDate timeStamp, int status, String error, String path, String errorCode, AccessScope scope, RestrictionLevel level, List<AccessRestriction> restrictions) {
        super(timeStamp, status, error, path);
        this.errorCode = errorCode;
        this.scope = scope;
        this.level = level;
        this.restrictions = restrictions;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public AccessScope getScope() {
        return scope;
    }

    public RestrictionLevel getLevel() {
        return level;
    }

    public List<AccessRestriction> getRestrictions() {
        return restrictions;
    }
}
