package com.travel_system.backend_app.policy;

import com.travel_system.backend_app.model.dtos.ownApi.ClosureSuspension;
import com.travel_system.backend_app.model.dtos.ownApi.RegistrationDeadlines;
import com.travel_system.backend_app.model.enums.RegistrationGraceReason;
import com.travel_system.backend_app.model.enums.RegistrationRequestStatus;
import com.travel_system.backend_app.model.enums.RegistrationRequestType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/*
* polítcas que definem como deve-se lidar com situações atipcas ao bloqueio, como aguardo de renovação e situações onde não existe um próxmo periódo
* */

public class RegistrationClosurePolicy {

    // dias para reenviar a renovação após a rejeição (apenas depois de esgotada a carência)
    public static final int RESUBMIT_AFTER_REJECTION_DAYS = 7;

    private RegistrationClosurePolicy() {
    }

    /*
     * resolve situações em que a matrícula não pode ser encerrada nem tratada como vencida, mesmo com o período encerrado.
     * usada pelo provider e pelo job para os dois nunca divergirem
     */
    public static Optional<ClosureSuspension> evaluate(boolean hasNextTerm, RegistrationRequestType openRequestType, RegistrationRequestStatus openRequestStatus, LocalDate lastRenewalRejectedOn, RegistrationDeadlines deadlines, LocalDate today) {
        // se sem o próximo período o usuário não é penalizado
        if (!hasNextTerm) {
            return Optional.of(new ClosureSuspension(RegistrationGraceReason.NEXT_TERM_MISSING, null));
        }

        // renovação enviada e aguardando a análise do admin
        if (openRequestType == RegistrationRequestType.RENEWAL
                && openRequestStatus == RegistrationRequestStatus.UNDER_REVIEW) {
            return Optional.of(new ClosureSuspension(RegistrationGraceReason.RENEWAL_UNDER_REVIEW, null));
        }

        // prazo para reenviar após a rejeição (só vale depois de esgotada a carência)
        if (lastRenewalRejectedOn != null && deadlines.isGraceExhausted(today)) {
            LocalDate resubmitDeadline = lastRenewalRejectedOn.plusDays(RESUBMIT_AFTER_REJECTION_DAYS);

            if (!today.isAfter(resubmitDeadline)) {
                return Optional.of(new ClosureSuspension(RegistrationGraceReason.RESUBMIT_AFTER_REJECTION, resubmitDeadline));
            }
        }

        return Optional.empty();
    }
}
