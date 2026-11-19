package com.travel_system.backend_app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_system.backend_app.exceptions.CustomerNotFoundException;
import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.model.dtos.ownApi.RegistrationSettings;
import com.travel_system.backend_app.model.dtos.ownApi.RegistrationStatusResult;
import com.travel_system.backend_app.model.dtos.ownApi.RegistrationStatusSnapshot;
import com.travel_system.backend_app.model.enums.OwnerType;
import com.travel_system.backend_app.policy.RegistrationStatusEvaluator;
import com.travel_system.backend_app.projections.RegistrationStatusView;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.repository.TransportRegistrationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * calcula a situação efetiva da matrícula de um titular (ou de vários) na data atual do Customer, com cache no Redis.
 * aqui não se aplica bloqueios, é a fonte usada pelas regras de restrição
 */

@Service
public class RegistrationStatusProvider {
    private final Logger log = LoggerFactory.getLogger(RegistrationStatusProvider.class);

    private static final String KEY_PREFIX = "registration-status:";
    private static final Duration MIN_TTL = Duration.ofMinutes(1);
    private static final Duration MAX_TTL = Duration.ofMinutes(15);

    private final TransportRegistrationRepository transportRegistrationRepository;
    private final CustomerRepository customerRepository;
    private final CustomerTimeService customerTimeService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RegistrationStatusProvider(TransportRegistrationRepository transportRegistrationRepository, CustomerRepository customerRepository, CustomerTimeService customerTimeService, StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.transportRegistrationRepository = transportRegistrationRepository;
        this.customerRepository = customerRepository;
        this.customerTimeService = customerTimeService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // pontos de entrada para um titular, monta a lista com um id e realza o reaproveitamento do fluxo em lute com forOwners
    public RegistrationStatusResult forStudent(UUID customerId, UUID studentId) {
        return forOwners(customerId, OwnerType.STUDENT, List.of(studentId)).get(studentId);
    }

    public RegistrationStatusResult forDriver(UUID customerId, UUID driverId) {
        return forOwners(customerId, OwnerType.DRIVER, List.of(driverId)).get(driverId);
    }

    // pontos de entrada em lote para relatórios e listas, retorna o map com id e resultado
    public Map<UUID, RegistrationStatusResult> forStudents(UUID customerId, Collection<UUID> studentIds) {
        return forOwners(customerId, OwnerType.STUDENT, studentIds);
    }

    public Map<UUID, RegistrationStatusResult> forDrivers(UUID customerId, Collection<UUID> driverIds) {
        return forOwners(customerId, OwnerType.DRIVER, driverIds);
    }

    // invalida o status de um titular (submissão, aprovação, rejeição, cancelamento, encerramento)
    public void evictOwner(UUID customerId, OwnerType ownerType, UUID ownerId) {
        String version = currentVersion(customerId);

        if (version == null) {
            return;
        }

        try {
            redisTemplate.delete(ownerKey(customerId, version, customerTimeService.today(customerId), ownerType, ownerId));
        } catch (Exception e) {
            log.warn("Falha ao invalidar o status da matrícula do titular {}: {}", ownerId, e.getMessage());
        }
    }

    // invalida todos os status do Customer (períodos e configurações)
    public void evictCustomer(UUID customerId) {
        try {
            redisTemplate.opsForValue().increment(versionKey(customerId));
        } catch (Exception e) {
            log.warn("Falha ao invalidar os status de matrícula do customer {}: {}", customerId, e.getMessage());
        }
    }

    /*
     * lê o cache em lote, calcula só os ausentes e grava o resultado até a meia-noite local
     * caso o Redis falhe, calcula tudo direto do banco
     */
    private Map<UUID, RegistrationStatusResult> forOwners(UUID customerId, OwnerType ownerType, Collection<UUID> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> ids = ownerIds.stream().distinct().toList();
        ZoneId zone = customerTimeService.zoneOf(customerId);
        LocalDate today = customerTimeService.today(customerId);
        String version = currentVersion(customerId);

        // sem o Redis, calcula direto
        if (version == null) {
            return load(customerId, ownerType, ids, zone, today);
        }

        Map<UUID, String> keys = new HashMap<>();
        ids.forEach(id -> keys.put(id, ownerKey(customerId, version, today, ownerType, id)));

        Map<UUID, RegistrationStatusResult> results = new HashMap<>(readCache(ids, keys));

        List<UUID> missing = ids.stream().filter(id -> !results.containsKey(id)).toList();

        if (!missing.isEmpty()) {
            Map<UUID, RegistrationStatusResult> loaded = load(customerId, ownerType, missing, zone, today);

            writeCache(keys, loaded, ttlUntilMidnight(today, zone));

            results.putAll(loaded);
        }

        return results;
    }

    /*
    * monta o registrationSettings e depois busca via consulta a matrícula não encerrada de cada titular
    * */
    private Map<UUID, RegistrationStatusResult> load(UUID customerId, OwnerType ownerType, List<UUID> ownerIds, ZoneId zone, LocalDate today) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer não encontrado"));

        RegistrationSettings settings = RegistrationSettings.from(customer);

        // matrículas não encerradas (no máximo uma por titular)
        Map<UUID, RegistrationStatusView> viewsByOwner = transportRegistrationRepository
                .findStatusViews(customerId, ownerType, ownerIds)
                .stream()
                .collect(Collectors.toMap(RegistrationStatusView::getOwnerId, Function.identity()));

        // titulares sem matrícula atual: verifica se existe alguma encerrada
        List<UUID> withoutCurrent = ownerIds.stream().filter(id -> !viewsByOwner.containsKey(id)).toList();

        Set<UUID> closedOwners = withoutCurrent.isEmpty() ? Set.of() : new HashSet<>(transportRegistrationRepository.findClosedOwnerIds(customerId, ownerType, withoutCurrent));

        Map<UUID, RegistrationStatusResult> results = new HashMap<>();

        for (UUID ownerId : ownerIds) {
            RegistrationStatusSnapshot snapshot = toSnapshot(viewsByOwner.get(ownerId), closedOwners.contains(ownerId), zone);
            results.put(ownerId, RegistrationStatusEvaluator.evaluate(snapshot, settings, today));
        }

        return results;
    }

    /*
    * realiza a conversão da consulta no resultado que o avaliador entende
    *
    * caso nao haja view, devolve fechado ou nenhum
    * com o view, pega o Instant e converte para a data com base no fuso do customer
    * */
    private RegistrationStatusSnapshot toSnapshot(RegistrationStatusView view, boolean closed, ZoneId zone) {
        if (view == null) {
            return closed ? RegistrationStatusSnapshot.closed() : RegistrationStatusSnapshot.none();
        }

        // a data da rejeição no fuso do Customer
        LocalDate lastRenewalRejectedOn = view.getLastRenewalRejectedAt() == null ? null : LocalDate.ofInstant(view.getLastRenewalRejectedAt(), zone);

        return new RegistrationStatusSnapshot(
                view.getStatus(),
                view.getTermEnd(),
                Boolean.TRUE.equals(view.getHasNextTerm()),
                view.getOpenRequestType(),
                view.getOpenRequestStatus(),
                lastRenewalRejectedOn
        );
    }

    /*
    * usa multiget para ler os valores todos de uma vez.
    * O redis retorna os valores com base nas ordens das chaves, por isso pode usar sempre o indice "i"
    * valor null ou que falhou na desserialzação sao ignorados
    * */
    private Map<UUID, RegistrationStatusResult> readCache(List<UUID> ids, Map<UUID, String> keys) {
        Map<UUID, RegistrationStatusResult> cached = new HashMap<>();

        try {
            List<String> values = redisTemplate.opsForValue().multiGet(ids.stream().map(keys::get).toList());

            if (values == null) {
                return cached;
            }

            for (int i = 0; i < ids.size(); i++) {
                String json = values.get(i);
                UUID ownerId = ids.get(i);

                if (json != null) {
                    deserialize(json).ifPresent(result -> cached.put(ownerId, result));
                }
            }
        } catch (RuntimeException e) {
            log.warn("Falha ao ler o cache de status de matrícula: {}", e.getMessage());
        }

        return cached;
    }


    /*
    * escreve cada resultado já calculado como json no redis, o ttl é até 00h
    *
    * como o resultado já foi calculado e resolvido, ele nao lida diretamente com falhas, apenas faz um log
    * */
    private void writeCache(Map<UUID, String> keys, Map<UUID, RegistrationStatusResult> results, Duration ttl) {
        try {
            results.forEach((ownerId, result) -> serialize(result)
                    .ifPresent(json -> redisTemplate.opsForValue().set(keys.get(ownerId), json, ttl)));
        } catch (Exception e) {
            log.warn("Falha ao gravar o cache de status de matrícula: {}", e.getMessage());
        }
    }

    // versão do Customer; null indica Redis indisponível
    private String currentVersion(UUID customerId) {
        try {
            String version = redisTemplate.opsForValue().get(versionKey(customerId));
            return version != null ? version : "0";
        } catch (Exception e) {
            log.warn("Redis indisponível para o status de matrícula: {}", e.getMessage());
            return null;
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

    // redis key
    private String ownerKey(UUID customerId, String version, LocalDate today, OwnerType ownerType, UUID ownerId) {
        return KEY_PREFIX + customerId + ":v" + version + ":" + today + ":" + ownerType + ":" + ownerId;
    }

    // redis key
    private String versionKey(UUID customerId) {
        return KEY_PREFIX + "version:" + customerId;
    }

    // conversões com objectmapper
    private Optional<String> serialize(RegistrationStatusResult result) {
        try {
            return Optional.of(objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            log.warn("Falha ao serializar o status de matrícula: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<RegistrationStatusResult> deserialize(String json) {
        try {
            return Optional.of(objectMapper.readValue(json, RegistrationStatusResult.class));
        } catch (JsonProcessingException e) {
            log.warn("Falha ao desserializar o status de matrícula: {}", e.getMessage());
            return Optional.empty();
        }
    }


}
