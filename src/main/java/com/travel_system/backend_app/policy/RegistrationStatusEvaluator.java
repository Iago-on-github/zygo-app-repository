package com.travel_system.backend_app.policy;

import com.travel_system.backend_app.model.dtos.ownApi.*;
import com.travel_system.backend_app.model.enums.EffectiveRegistrationStatus;
import com.travel_system.backend_app.model.enums.RegistrationGraceReason;
import com.travel_system.backend_app.model.enums.RegistrationRequestType;

import java.time.LocalDate;
import java.util.Optional;

/*
* realiza os cálculos de periodo vigente, carência, etc, e os mantém separados
* */

public class RegistrationStatusEvaluator {

    private RegistrationStatusEvaluator() {
    }

    public static RegistrationStatusResult evaluate(RegistrationStatusSnapshot snapshot, RegistrationSettings settings, LocalDate today) {
        // sem matrícula
        if (snapshot.status() == null) {
            return RegistrationStatusResult.of(EffectiveRegistrationStatus.NONE);
        }

        return switch (snapshot.status()) {
            case CLOSED -> RegistrationStatusResult.of(EffectiveRegistrationStatus.CLOSED);
            case PENDING -> RegistrationStatusResult.of(EffectiveRegistrationStatus.PENDING);
            // INACTIVE é tratado como ACTIVE no cálculo das datas
            case ACTIVE, INACTIVE -> evaluateCurrent(snapshot, settings, today);
        };
    }

    private static RegistrationStatusResult evaluateCurrent(RegistrationStatusSnapshot snapshot, RegistrationSettings settings, LocalDate today) {
        if (snapshot.termEnd() == null) {
            throw new IllegalStateException("Matrícula vigente sem período associado");
        }

        RegistrationDeadlines deadlines = RegistrationDeadlines.of(snapshot.termEnd(), settings);

        boolean windowOpen = deadlines.isWindowOpen(today);
        boolean renewalInProgress = snapshot.openRequestType() == RegistrationRequestType.RENEWAL;

        // se período ainda vigente
        if (deadlines.isWithinTerm(today)) {
            return new RegistrationStatusResult(EffectiveRegistrationStatus.ACTIVE, null, null,
                    deadlines, windowOpen, renewalInProgress);
        }

        // se período encerrado verifica as suspensões antes da carência padrão
        Optional<ClosureSuspension> suspension = RegistrationClosurePolicy.evaluate(
                snapshot.hasNextTerm(),
                snapshot.openRequestType(),
                snapshot.openRequestStatus(),
                snapshot.lastRenewalRejectedOn(),
                deadlines,
                today
        );

        if (suspension.isPresent()) {
            return new RegistrationStatusResult(EffectiveRegistrationStatus.GRACE, suspension.get().reason(), suspension.get().deadline(),
                    deadlines, windowOpen, renewalInProgress);
        }

        // carência padrão
        if (deadlines.isInGrace(today)) {
            return new RegistrationStatusResult(EffectiveRegistrationStatus.GRACE, RegistrationGraceReason.STANDARD, deadlines.graceEnd(), deadlines, windowOpen, renewalInProgress);
        }

        // carência esgotada e o job ainda não encerrou
        return new RegistrationStatusResult(EffectiveRegistrationStatus.EXPIRED, null, null, deadlines, windowOpen, renewalInProgress);
    }
}
