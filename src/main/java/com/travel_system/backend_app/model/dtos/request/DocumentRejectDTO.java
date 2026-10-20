package com.travel_system.backend_app.model.dtos.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public record DocumentRejectDTO(@NotBlank @Size(max = 500) String rejectReason) {
}
