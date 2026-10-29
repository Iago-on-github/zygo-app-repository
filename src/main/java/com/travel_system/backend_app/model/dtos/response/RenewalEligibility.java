package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.RenewalBlockReason;

import java.time.LocalDate;

/*
 * resultado da avaliação de renovação
 * - canRenew: todas as regras passam hoje
 * - availableFrom: data de abertura da janela (null se não houver período)
 * - blockReason: motivo do bloqueio (null quando canRenew)
 * */
public record RenewalEligibility(
        boolean canRenew,
        LocalDate availableFrom,
        RenewalBlockReason blockReason
) {
    public static RenewalEligibility allowed(LocalDate availableFrom) {
        return new RenewalEligibility(true, availableFrom, null);
    }

    public static RenewalEligibility blocked(RenewalBlockReason reason, LocalDate availableFrom) {
        return new RenewalEligibility(false, availableFrom, reason);
    }
}
