package com.travel_system.backend_app.model.dtos.ownApi;

import com.travel_system.backend_app.model.enums.AccessCategory;
import com.travel_system.backend_app.model.enums.RestrictionLevel;

import java.util.List;
import java.util.Set;

/*
 * representa o nível efetivo, a lista de restrições e os escopos bloqueados
 *
 * é oq o front mostra para o user bloqueado, e também oq fica em cache L1
 * */

public record AccessRestrictionSummary(
        RestrictionLevel lvl, // o nível
        List<AccessRestriction> restrictions, // os avisos e os bloqueios, com motivos e prazos
        Set<AccessCategory> blockedCategories
) {
}
