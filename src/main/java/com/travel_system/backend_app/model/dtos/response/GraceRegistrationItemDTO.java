package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.model.enums.RenewalProgressStatus;

import java.time.LocalDate;
import java.util.UUID;

/*
 * item da lista de matrículas em carência
 * - graceEnd: último dia de acesso (fim do período + carência)
 * - daysUntilBlock: dias até o bloqueio (0 = hoje é o último dia)
 * */
public record GraceRegistrationItemDTO(
        UUID registrationId,
        String registrationNumber,
        OwnerType ownerType,
        String ownerName,
        LocalDate termEnd,
        LocalDate graceEnd,
        long daysUntilBlock,
        RenewalProgressStatus renewalStatus
) {
}
