package com.travel_system.backend_app.model.dtos.response;

/*
 * item da lista de matrículas paradas
 * - since: última atividade do rascunho ou data da rejeição
 * - documentsSent: documentos já enviados no rascunho (0 na rejeição: os documentos são apagados ao rejeitar)
 * */

import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.model.enums.StalledReason;

import java.time.Instant;
import java.util.UUID;

public record StalledRegistrationItemDTO(
        UUID registrationId,
        OwnerType ownerType,
        String ownerName,
        StalledReason reason,
        Instant since,
        long daysStalled,
        long documentsSent
) {
}
