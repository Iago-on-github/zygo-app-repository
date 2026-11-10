package com.travel_system.backend_app.model.dtos.ownApi;

/*
 * prazos de uma matrícula, calculados a partir do fim do período e das configurações do Customer
 * */

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record RegistrationDeadlines(
        LocalDate termEnd,           // último dia de validade
        LocalDate windowStart,       // abertura da janela de renovação
        LocalDate graceEnd,          // último dia de acesso (fim da carência)
        LocalDate blockWarningStart  // início do aviso de bloqueio
) {
    public static RegistrationDeadlines of(LocalDate termEnd, RegistrationSettings settings) {
        LocalDate graceEnd = termEnd.plusDays(settings.gracePeriodDays());

        return new RegistrationDeadlines(
                termEnd,
                termEnd.minusDays(settings.renewalWindowDays()),
                graceEnd,
                graceEnd.minusDays(settings.blockWarningDays()));
    }

    // janela de renovação aberta (inclui a carência)
    public boolean isWindowOpen(LocalDate today) {
        return !today.isBefore(windowStart);
    }

    // período vigente (ainda dentro da validade)
    public boolean isWithinTerm(LocalDate today) {
        return !today.isAfter(termEnd);
    }

    // período vencido, ainda com acesso
    public boolean isInGrace(LocalDate today) {
        return today.isAfter(termEnd) && !today.isAfter(graceEnd);
    }

    // carência esgotada
    public boolean isGraceExhausted(LocalDate today) {
        return today.isAfter(graceEnd);
    }

    // dentro do prazo do aviso de bloqueio (ainda com acesso)
    public boolean isInBlockWarning(LocalDate today) {
        return !today.isBefore(blockWarningStart) && !today.isAfter(graceEnd);
    }

    public long daysUntilTermEnd(LocalDate today) {
        return ChronoUnit.DAYS.between(today, termEnd);
    }

    public long daysUntilBlock(LocalDate today) {
        return ChronoUnit.DAYS.between(today, graceEnd);
    }
}
