package com.travel_system.backend_app.model.dtos.response;


import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/*
 * resumo geral das matrículas do Customer
 * */

public record RegistrationSummaryDTO(
    RegistrationStatusSummary registrations,
    ReviewQueueSummary reviewQueue,
    RenewalProgressSummary renewal,
    SummaryContext context
) {

    /*
     * grupo A: situação das matrículas
     * - active: ACTIVE e período vigente (endTerm >= hoje)
     * - inGrace: ACTIVE, período vencido, ainda dentro da carência
     * - awaitingClosure: ACTIVE, carência esgotada, aguardando o job encerrar (normalmente 0)
     * */
    public record RegistrationStatusSummary(
            OwnerTypeCount active,
            OwnerTypeCount inGrace,
            long awaitingClosure,
            long pending,
            long closed,
            long inactive
    ) {
    }

    /*
     * contagem total e dividida por tipo de titular (a secretaria planeja alunos e motoristas de forma diferente)
     * */
    public record OwnerTypeCount(
            long total,
            long students,
            long drivers
    ) {
    }

    /*
     * grupo B: fila de análise (solicitações em UNDER_REVIEW)
     * - oldestSubmittedAt: envio mais antigo ainda aguardando (null se a fila estiver vazia)
     * */
    public record ReviewQueueSummary(
            long total,
            long initial,
            long renewal,
            long update,
            Instant oldestSubmittedAt
    ) {
    }

    /*
     * grupo C: andamento da renovação (matrículas ACTIVE com a janela de renovação aberta, inclusive em carência)
     * - notStarted + inDraft + underReview = total
     * - blockedByMissingTerm: alerta que se sobrepõe aos demais (o próximo período não foi cadastrado)
     * */
    public record RenewalProgressSummary(
            long total,
            long notStarted,
            long inDraft,
            long underReview,
            long blockedByMissingTerm
    ) {
    }

    /*
     * grupo D: contexto do cálculo
     * - referenceDate: data usada nas regras (fuso America/Sao_Paulo)
     * - currentTerm: null se não houver período vigente
     * */
    public record SummaryContext(
            LocalDate referenceDate,
            CurrentTermInfo currentTerm,
            boolean nextTermRegistered,
            RegistrationSettingsInfo settings
    ) {
    }

    public record CurrentTermInfo(
            UUID id,
            String name,
            LocalDate startTerm,
            LocalDate endTerm
    ) {
    }

    // configurações usadas no cálculo (o admin entende por que alguém está em carência ou bloqueio)
    public record RegistrationSettingsInfo(
            int gracePeriodDays,
            int renewalWindowDays,
            int blockWarningDays
    ) {
    }
}
