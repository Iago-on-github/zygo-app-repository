package com.travel_system.backend_app.model.dtos.request;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;

/*
* só a versão exibida + confirmação explícita
 * */

public record HealthConsentRequestDTO(
        @NotBlank String termVersion,
        @AssertTrue boolean accepted
) {
}
