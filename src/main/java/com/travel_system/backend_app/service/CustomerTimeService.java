package com.travel_system.backend_app.service;

import com.travel_system.backend_app.model.Customer;
import com.travel_system.backend_app.repository.CustomerRepository;
import com.travel_system.backend_app.utils.CustomerZoneCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.UUID;

import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;

/*
* responsável por gerenciar o fuso do Customer, respondendo perguntas como "que dia é hoje" e "que horas são"
* a partir da região na qual o customer está
* todas as regras que envolvem diretamente ou indiretamente datas de customer passarão por aqui
* */

@Service
public class CustomerTimeService {
    private final Logger log = LoggerFactory.getLogger(CustomerTimeService.class);

    private final CustomerRepository customerRepository;

    private final CustomerZoneCache customerZoneCache;

    private final Clock clock;

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    public CustomerTimeService(CustomerRepository customerRepository, CustomerZoneCache customerZoneCache, Clock clock) {
        this.customerRepository = customerRepository;
        this.customerZoneCache = customerZoneCache;
        this.clock = clock;
    }

    /*
    * resolve o timezone devolvendo um zoneId estável
    * */
    public ZoneId resolveZone(String zone) {
        if (zone == null || zone.isBlank()) {
            return DEFAULT_ZONE;
        }

        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            // se falhar
            log.warn("[CustomerTimeService] Fuso inválido '{}', usando o padrão {}", zone, DEFAULT_ZONE);

            return DEFAULT_ZONE;
        }
    }

    /*
    * padronzação de operações
    * */

    // fuso a partir da entidade p/ jobs: sem consulta e sem cache)
    public ZoneId zoneOf(Customer customer) {
        return resolveZone(customer.getTimeZone());
    }

    // fuso a partir do id (requisições: pelo cache)
    public ZoneId zoneOf(UUID customerId) {
        return resolveZone(customerZoneCache.getZoneId(customerId));
    }

    // fuso do Customer da requisição atual
    private ZoneId currentZone() {
        return zoneOf(getCurrentTenant());
    }

    /*
     * data de hoje no fuso do Customer
     * */
    public LocalDate today() {
        return today(currentZone());
    }

    public LocalDate today(Customer customer) {
        return today(zoneOf(customer));
    }

    public LocalDate today(UUID customerId) {
        return today(zoneOf(customerId));
    }

    private LocalDate today(ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }

    /*
     * data e hora locais do Customer (horários exibidos, viagens)
     * */
    public ZonedDateTime now() {
        return ZonedDateTime.now(clock.withZone(currentZone()));
    }

    public ZonedDateTime now(Customer customer) {
        return ZonedDateTime.now(clock.withZone(zoneOf(customer)));
    }

    /*
     * instante atual (para gravar createdAt, reviewedAt etc.):
     * é sem fuso, mas controlável nos testes
     * */
    public Instant instant() {
        return clock.instant();
    }

    /*
     * início de um dia local do Customer, como Instant (para comparar com campos Instant)
     * atStartOfDay(zone) trata dias em que a meia-noite não existe (horário de verão)
     * */
    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(currentZone()).toInstant();
    }

    public Instant startOfDay(Customer customer, LocalDate date) {
        return date.atStartOfDay(zoneOf(customer)).toInstant();
    }

    /*
     * instante convertido em data local do Customer (ex.: dias parados, datas em notificações)
     * */
    public LocalDate toLocalDate(Instant instant) {
        return LocalDate.ofInstant(instant, currentZone());
    }

    public LocalDate toLocalDate(Customer customer, Instant instant) {
        return LocalDate.ofInstant(instant, zoneOf(customer));
    }

}
