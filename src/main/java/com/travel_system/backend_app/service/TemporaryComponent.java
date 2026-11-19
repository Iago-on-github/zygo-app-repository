package com.travel_system.backend_app.service;

import com.travel_system.backend_app.interfaces.RestrictionRule;
import com.travel_system.backend_app.model.dtos.ownApi.AccessRestriction;
import com.travel_system.backend_app.model.dtos.ownApi.RestrictionSubject;
import com.travel_system.backend_app.model.enums.RestrictionCode;
import com.travel_system.backend_app.model.enums.RestrictionLevel;
import com.travel_system.backend_app.model.enums.RestrictionPhase;
import com.travel_system.backend_app.model.enums.UserAccountType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

import static com.travel_system.backend_app.resolvers.AuthenticatedUserResolver.getAuthenticatedUserUUID;

@Component
public class TemporaryComponent implements RestrictionRule {
    @Override
    public boolean appliesTo(UserAccountType type) {
        return type == UserAccountType.STUDENT;
    }

    @Override
    public Optional<AccessRestriction> evaluate(RestrictionSubject subject) {
        if (subject.userAccountId().equals(getAuthenticatedUserUUID().get())) {
            return Optional.of(new AccessRestriction(
                    RestrictionCode.REQUIRE_TRANSPORT_REGULARIZATION,
                    RestrictionLevel.LEVEL_1,
                    RestrictionPhase.WARNING,
                    "Mensagem block de teste",
                    LocalDate.of(2026, 12, 12)));
        }

        return Optional.empty();
    }
}
