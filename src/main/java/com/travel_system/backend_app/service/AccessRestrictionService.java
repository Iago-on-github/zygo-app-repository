package com.travel_system.backend_app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.interfaces.RestrictionRule;
import com.travel_system.backend_app.model.dtos.ownApi.AccessRestriction;
import com.travel_system.backend_app.model.dtos.ownApi.AccessRestrictionSummary;
import com.travel_system.backend_app.model.dtos.ownApi.RestrictionSubject;
import com.travel_system.backend_app.model.enums.*;
import org.hibernate.mapping.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/*
* responsável por executar as regras que se aplicam
* calcular o nível efetivo e a lista de escopos bloqueados
* */

@Service
public class AccessRestrictionService {
    private final Logger log = LoggerFactory.getLogger(AccessRestrictionService.class);

    private final StringRedisTemplate redisTemplate;

    private final List<RestrictionRule> restrictionRules;

    private final CustomerTimeService customerTimeService;

    private final static String REDIS_KEY_PREFIX = "access-restrictions:";
    private static final Duration MIN_TTL = Duration.ofMinutes(1);
    private static final Duration MAX_TTL = Duration.ofMinutes(15);

    private final ObjectMapper objectMapper;

    public AccessRestrictionService(StringRedisTemplate redisTemplate, List<RestrictionRule> restrictionRules, CustomerTimeService customerTimeService, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.restrictionRules = restrictionRules;
        this.customerTimeService = customerTimeService;
        this.objectMapper = objectMapper;
    }

    public AccessRestrictionSummary getSummary(RestrictionSubject subject) {
        if (subject == null) {
            return new AccessRestrictionSummary(RestrictionLevel.NONE, List.of(), Set.of());
        }

        UUID customerId = subject.customerId();

        // contexto do customer, calculado uma vez (a mesma data na key e no ttl)
        ZoneId zoneId = customerTimeService.zoneOf(customerId);
        LocalDate today = customerTimeService.today(customerId);

        String version = currentVersion(customerId);

        // redis indisponível: calcula sem cache
        if (version == null) {
            return computeData(subject).summary();
        }

        String ownerKey = ownerKey(customerId, version, today, subject.userProfileId());

        Optional<AccessRestrictionSummary> cached = readRedis(ownerKey);

        if (cached.isPresent()) {
            return cached.get();
        }

        ComputedSummary computed = computeData(subject);

        // grava só se todas as regras foram avaliadas (resultado parcial não vai para o cache)
        if (computed.complete()) {
            writeAtRedis(ownerKey, computed.summary(), ttlUntilMidnight(today, zoneId));
        }

        return computed.summary();
    }

    // carrega os métodos e monta o summary sem cache
    private ComputedSummary computeData(RestrictionSubject subject) {
        RuleEvaluation ruleEvaluation = executionRules(subject);

        RestrictionLevel restrictionLevel = effectiveLevel(ruleEvaluation.restrictions);
        EnumSet<AccessScope> accessScopes = blockedScopes(restrictionLevel);

        AccessRestrictionSummary summary = new AccessRestrictionSummary(restrictionLevel, ruleEvaluation.restrictions(), accessScopes);

        return new ComputedSummary(summary, ruleEvaluation.complete());
    }

    private RuleEvaluation executionRules(RestrictionSubject subject) {
        List<AccessRestriction> presents = new ArrayList<>();

        for (RestrictionRule restrictionRule : restrictionRules) {
            if (restrictionRule.appliesTo(subject.userType())) {
                try {
                    Optional<AccessRestriction> evaluate = restrictionRule.evaluate(subject);
                    evaluate.ifPresent(presents::add);
                } catch (Exception e) {
                    /*
                        * trata as falhas como abertas
                        * o erro apenas é ligado ao log e o resultado não chega ao cache, a prox requiisição tenta denovo.
                        * */
                    log.error("[executionRules] - ocorreu um erro durante a execução das regras, cause ", e);
                    return new RuleEvaluation(List.of(), false);
                }
            }
        }

        // ordena a lista
        // primeiro o maior nível, e os BLOCK antes dos WARNING
        List<AccessRestriction> accessRestrictionsOrdered = presents.stream()
                .sorted(Comparator.comparing((AccessRestriction r) -> r.level().getValue()).reversed()
                        .thenComparing(r -> r.phase() == RestrictionPhase.BLOCK ? 0 : 1))
                .toList();

        return new RuleEvaluation(accessRestrictionsOrdered, true);
    }

    private RestrictionLevel effectiveLevel(List<AccessRestriction> restrictions) {
        // considera somente as restrições em block, pega o maior nível e, se não houver, trata como NONE
        Integer calculatedLvl = restrictions.stream()
                .filter(rest -> rest.phase() == RestrictionPhase.BLOCK)
                .map(lvl -> lvl.level().getValue())
                .max(Integer::compareTo)
                .orElse(0);

        return RestrictionLevel.fromValue(calculatedLvl);
    }

    private EnumSet<AccessScope> blockedScopes(RestrictionLevel effectiveLevel) {
        EnumSet<AccessScope> blockedScopes = EnumSet.allOf(AccessScope.class);

        // percorre todos e mantém os que estão bloqueados no nível em questão
        blockedScopes.removeIf(scope -> !scope.isBlockedAt(effectiveLevel));

        return blockedScopes;
    }

    // apaga a key do titular (juntamente com a versão e a data atual)
    public void evictOwner(UUID customerId, UUID ownerId) {
        String version = currentVersion(customerId);

        if (version == null) {
            return;
        }

        try {
            redisTemplate.delete(ownerKey(customerId, version, customerTimeService.today(customerId), ownerId));
        } catch (Exception e) {
            log.warn("Falha durante a exclusão da key: ", e);
        }
    }

    // incrementa a versão do customer sem fazer varredura nas keys
    public void evictCustomer(UUID customerId) {
        try {
            redisTemplate.opsForValue().increment(customerVersionKey(customerId));
        } catch (Exception e) {
            log.warn("Falha ao invalidar {}: {}", customerId, e.getMessage());

        }
    }

    /*
     * menor entre o tempo até as 00h do dia seguinte com base no fuso do customer
     * */
    private Duration ttlUntilMidnight(LocalDate today, ZoneId zone) {
        Instant midnight = today.plusDays(1).atStartOfDay(zone).toInstant();

        Duration untilMidnight  = Duration.between(customerTimeService.instant(), midnight);

        Duration ttl = untilMidnight.compareTo(MAX_TTL) > 0 ? MAX_TTL : untilMidnight;

        return ttl.compareTo(MIN_TTL) < 0 ? MIN_TTL : ttl;
    }

    // devolve vazio em caso de falha
    private Optional<AccessRestrictionSummary> readRedis(String key) {
        try {

            String cached = redisTemplate.opsForValue().get(key);

            if (cached == null) {
                return Optional.empty();
            }

            return deserializeJsonAsResume(cached);
        } catch (Exception e) {
            log.warn("Falha ao ler o cache: {}", e.getMessage());

            return Optional.empty();
        }
    }

    private void writeAtRedis(String key, AccessRestrictionSummary summary, Duration ttl) {
        try {
            writeResumeAsJson(summary).ifPresent(json -> redisTemplate.opsForValue().set(key, json, ttl));
        } catch (Exception e) {
            log.warn("Falha ao gravar o cache: {}", e.getMessage());
        }
    }

    private String customerVersionKey(UUID customerId) {
        return REDIS_KEY_PREFIX + "version:" + customerId;
    }

    // key do dono no redis (todos vem do subject)
    private String ownerKey(UUID customerId, String version, LocalDate today, UUID ownerId) {
        return REDIS_KEY_PREFIX + customerId + ":v" + version + ":" + today + ":" + ":" + ownerId;
    }

    private Optional<String> writeResumeAsJson(AccessRestrictionSummary summary) {
        try {
            return Optional.of(objectMapper.writeValueAsString(summary));
        } catch (JsonProcessingException e) {
            log.warn("Falha ao serializar: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<AccessRestrictionSummary> deserializeJsonAsResume(String json) {
        try {
            return Optional.of(objectMapper.readValue(json, AccessRestrictionSummary.class));
        } catch (JsonProcessingException e) {
            log.warn("Falha ao desserializar: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // versão do customer: devolve null caso o redis falhe
    private String currentVersion(UUID customerId) {
        try {
            String version = redisTemplate.opsForValue().get(customerVersionKey(customerId));

            return version != null ? version : "0";
        } catch (Exception e ) {
            log.warn("Redis indisponível: {}", e.getMessage());
            return null;
        }
    }

    private record RuleEvaluation(
            List<AccessRestriction> restrictions,
            boolean complete
    ) {}

    private record ComputedSummary(AccessRestrictionSummary summary, boolean complete) {}
}
