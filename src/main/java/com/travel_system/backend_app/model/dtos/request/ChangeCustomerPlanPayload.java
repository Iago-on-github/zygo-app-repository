package com.travel_system.backend_app.model.dtos.request;

import com.travel_system.backend_app.interfaces.SensitiveOperationData;
import com.travel_system.backend_app.model.enums.CustomerPlan;
import com.travel_system.backend_app.model.enums.SensitiveOperationType;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public record ChangeCustomerPlanPayload(
        @NotBlank
        String cnpj,

        @NotNull
        CustomerPlan newPlan
) implements SensitiveOperationData {
    @Override
    public SensitiveOperationType sensitiveType() {
        return SensitiveOperationType.CHANGE_CUSTOMER_PLAN;
    }
}
