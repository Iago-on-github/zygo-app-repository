package com.travel_system.backend_app.resolvers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.travel_system.backend_app.config.TokenConfig;
import com.travel_system.backend_app.exceptions.CustomerNotFoundException;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.model.dtos.ProfileRef;
import com.travel_system.backend_app.model.dtos.ownApi.RestrictionSubject;
import com.travel_system.backend_app.model.enums.UserAccountType;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;

/*
* responsável por montar o RestrictionSubject para as entidades de domínio elegíveis. Os demais recebem null
*
* usa cache L1 para esses dados
* */

@Component
public class RestrictionSubjectResolver {
    private final Logger log = LoggerFactory.getLogger(RestrictionSubjectResolver.class);

    private final UserAccountRepository userAccountRepository;

    private final UserProfileResolver userProfileResolver;

    private final Cache<UUID, Optional<RestrictionSubject>> cache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .build();

    private final static List<UserAccountType> eligibleEntityTypes = List.of(UserAccountType.STUDENT, UserAccountType.DRIVER, UserAccountType.RESPONSIBLE_ADULT);

    public RestrictionSubjectResolver(UserAccountRepository userAccountRepository, UserProfileResolver userProfileResolver) {
        this.userAccountRepository = userAccountRepository;
        this.userProfileResolver = userProfileResolver;
    }

    public Optional<RestrictionSubject> buildRestrictionSubject(UUID userAccountId) {
        /*
        * faz a leitura do cache
        * se for null, escreve de forma atômica no cache e protege contra requisições simultâneas
        * */
        return cache.get(userAccountId, this::load);
    }

    // evict de cache para que usuários unassigned não tenham perms que ainda não poderia
    // chamado no aceite do convite e na saída do customer via after commit event
    // apenas para entidades elegiveis para as restrições
    public void evict(UUID userAccountId) {
        try {
            cache.invalidate(userAccountId);
        } catch (Exception e) {
            log.warn("Falha ao invalidar os dados de cache para a key: {}, cause: {}", userAccountId, e.getMessage());
        }
    }

    private Optional<RestrictionSubject> load(UUID userAccountId) {
        // buscar o tipo da conta
        UserAccount userAccount = userAccountRepository.findById(userAccountId)
                .orElse(null);

        if (userAccount == null) {
            return Optional.empty();
        }

        // caso não seja do tipo elegível, não continua
        boolean hasEligibleType = eligibleEntityTypes.stream().anyMatch(type -> type == userAccount.getUserAccountType());

        if (!hasEligibleType) {
            return Optional.empty();
        }

        // busca o id e o customerId do perfil
        return userProfileResolver.resolveEligibleEntitiesForRestriction(userAccount)
                .map(ref -> new RestrictionSubject(userAccountId, userAccount.getUserAccountType(), ref.customerId(), ref.ownerId()));
    }
}
