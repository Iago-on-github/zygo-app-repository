package com.travel_system.backend_app.model.dtos.response;

/*
* Centraliza o cálculo de datas evitando repetição em diferentes locais e possíveis divergências futuras
* */

import java.time.LocalDate;

public record RegistrationDeadlines(
        LocalDate endTerm,
        LocalDate windowStart,
        LocalDate graceEnd,
        LocalDate blockWarningStart
) {
}
