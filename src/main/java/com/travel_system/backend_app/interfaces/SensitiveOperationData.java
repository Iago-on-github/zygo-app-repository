package com.travel_system.backend_app.interfaces;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;

public interface SensitiveOperationData {

    @JsonIgnore
    SensitiveOperationType sensitiveType();
}
