package com.travel_system.backend_app.model.dtos.response;

import com.travel_system.backend_app.model.enums.TransportRegistrationStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TransportRegistrationResponseDTO(
        UUID registrationId,
        String registrationNumber,           // null até a primeira aprovação
        TransportRegistrationStatus status,
        LocalDate validUntil,                // customerTerm.endTerm (null enquanto PENDING)
        String photoUrl,                     // URL temporária (pendente ou atual)
        RegistrationRequestSummaryDTO openRequest,      // null se não houver
        List<RegistrationChecklistItemDTO> checklist,   // vazio se não houver solicitação aberta
        boolean photoRequired,
        boolean photoSent,
        RegistrationRejectionDTO lastRejection          // preenchido só quando não há solicitação aberta e a última foi rejeitada

) {
}
