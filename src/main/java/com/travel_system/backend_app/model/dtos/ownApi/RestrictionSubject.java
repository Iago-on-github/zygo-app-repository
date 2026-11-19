package com.travel_system.backend_app.model.dtos.ownApi;

/*
* representa quem está sendo avaliado
* deve ser montado apenas uma vez por requisição
* */

import com.travel_system.backend_app.model.enums.UserAccountType;

import java.util.UUID;

public record RestrictionSubject(
        UUID userAccountId,
        UserAccountType userType,
        UUID customerId,
        UUID userProfileId // id do perfil da entiade de domínio (student, driver, responsible)
) {
}
