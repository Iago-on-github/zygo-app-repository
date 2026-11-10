package com.travel_system.backend_app.model.dtos.ownApi;

/*
 * limites de data para as consultas (comparados com o endTerm de cada matrícula)
 * - em carência: graceLimit <= endTerm < today
 * - janela aberta: endTerm <= windowLimit
 * - bloqueio próximo: endTerm <= blockingSoonLimit
 * */

import java.time.LocalDate;

public record RegistrationQueryLimits(
        LocalDate today,
        LocalDate graceLimit,
        LocalDate windowLimit,
        LocalDate blockingSoonLimit
) {
    public static RegistrationQueryLimits of(LocalDate today, RegistrationSettings settings) {
        return new RegistrationQueryLimits(
                today,
                today.minusDays(settings.gracePeriodDays()),
                today.plusDays(settings.renewalWindowDays()),
                today.plusDays(settings.blockWarningDays()).minusDays(settings.gracePeriodDays()));
    }
}
