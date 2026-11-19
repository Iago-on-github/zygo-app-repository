package com.travel_system.backend_app.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.travel_system.backend_app.model.UserAccount;
import com.travel_system.backend_app.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/*
* gerencia a atividade (período de uso) dos usuários
* */

@Service
public class UserActivityService {
    private final Logger log = LoggerFactory.getLogger(UserActivityService.class);

    private final UserAccountRepository userAccountRepository;

    private final AccountReactivationService reactivationService;
    private final CustomerTimeService customerTimeService;

    private final Cache<UUID, Boolean> recentlyActivity = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(100_000)
            .build();

    public UserActivityService(UserAccountRepository userAccountRepository, AccountReactivationService reactivationService, CustomerTimeService customerTimeService) {
        this.userAccountRepository = userAccountRepository;
        this.reactivationService = reactivationService;
        this.customerTimeService = customerTimeService;
    }

    /*
    * registra atividade do usuário e usa caffeine para cachear o resultado
    * */
    @Transactional
    public void recordActivity(UUID userAccountId) {
        if (userAccountId == null) {
            return;
        }

        Boolean isPreset = recentlyActivity.getIfPresent(userAccountId);

        // se user presente n faz nada
        if (Boolean.TRUE.equals(isPreset)) {
            return;
        }

        // só adiciona ao caffeine caso tudo dê certo, se não, a prox. req tenta novamente
        try {
            // se o inactiveAt estiver preenchido chama a reativação de conta e joga o user no caffeine novamente
            boolean withInactive = userAccountRepository.existsInactiveUserById(userAccountId);

            // chama reativação
            if (withInactive) {
                reactivationService.reactivate(userAccountId);
            }

            Instant lastActivityAt = customerTimeService.instant();

            Instant twentyFourHoursAgo = lastActivityAt.minus(Duration.ofHours(24));

            // caso esteja presente, executa consulta condicional
            userAccountRepository.recordUserLastActivity(userAccountId, lastActivityAt, twentyFourHoursAgo);

            // caso não haja linhas afetadas houve atividae nas últimas 24h e o user não pode estar inativo
            // coloca user no caffeine e finaliza
            recentlyActivity.put(userAccountId, true);
        } catch (Exception e) {
            log.warn("[recordActivity] - Ocorreu um erro durante o registro de atividade do usuário {}", userAccountId, e);
        }

    }
}
