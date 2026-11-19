package com.travel_system.backend_app.interfaces;

import com.travel_system.backend_app.model.dtos.ownApi.AccessRestriction;
import com.travel_system.backend_app.model.dtos.ownApi.RestrictionSubject;
import com.travel_system.backend_app.model.enums.RestrictionCode;
import com.travel_system.backend_app.model.enums.UserAccountType;
import org.springframework.stereotype.Component;

import java.util.Optional;

public interface RestrictionRule {
    boolean appliesTo(UserAccountType type); // diz para qual entidade de domínio a regra vale
    Optional<AccessRestriction> evaluate(RestrictionSubject subject); // devolve uma restrição ou null
}
