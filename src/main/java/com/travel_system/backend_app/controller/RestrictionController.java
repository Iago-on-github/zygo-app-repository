package com.travel_system.backend_app.controller;

import com.travel_system.backend_app.annotations.RequireAccess;
import com.travel_system.backend_app.model.dtos.ownApi.AccessRestrictionSummary;
import com.travel_system.backend_app.model.dtos.ownApi.RestrictionSubject;
import com.travel_system.backend_app.model.dtos.ownApi.RestrictionsResponseDTO;
import com.travel_system.backend_app.model.enums.RestrictionLevel;
import com.travel_system.backend_app.resolvers.RestrictionSubjectResolver;
import com.travel_system.backend_app.service.AccessRestrictionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.travel_system.backend_app.resolvers.AuthenticatedUserResolver.getAuthenticatedUserUUID;

@RestController
@RequestMapping("/v1/me/restrictions")
@RequireAccess(AccessScope.REGULARIZATION)
public class RestrictionController {

    private final RestrictionSubjectResolver restrictionSubjectResolver;
    private final AccessRestrictionService accessRestrictionService;

    public RestrictionController(RestrictionSubjectResolver restrictionSubjectResolver, AccessRestrictionService accessRestrictionService) {
        this.restrictionSubjectResolver = restrictionSubjectResolver;
        this.accessRestrictionService = accessRestrictionService;
    }

    @GetMapping("/my")
    public ResponseEntity<RestrictionsResponseDTO> getMyRestrictions() {
        RestrictionsResponseDTO empty = new RestrictionsResponseDTO(RestrictionLevel.NONE, List.of(), Set.of());

        Optional<UUID> userAccountId = getAuthenticatedUserUUID();

        if (userAccountId.isEmpty()) {
            return ResponseEntity.ok().body(empty);
        }

        Optional<RestrictionSubject> subject = restrictionSubjectResolver.buildRestrictionSubject(userAccountId.get());

        // se vazio são administradores, plataforma ou contas sem customer
        if (subject.isEmpty()) {
            return ResponseEntity.ok().body(empty);
        }

        AccessRestrictionSummary summary = accessRestrictionService.getSummary(subject.get());

        return ResponseEntity.ok(new RestrictionsResponseDTO(summary.lvl(), summary.restrictions(), summary.blockedScopes()));
    }
}
