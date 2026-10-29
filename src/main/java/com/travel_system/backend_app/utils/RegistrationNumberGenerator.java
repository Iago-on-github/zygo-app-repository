package com.travel_system.backend_app.utils;

import com.travel_system.backend_app.exceptions.CustomerNotFoundException;
import com.travel_system.backend_app.model.RegistrationRequest;
import com.travel_system.backend_app.repository.RegistrationNumberSequenceRepository;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.travel_system.backend_app.infrastructure.TenantContext.getCurrentTenant;

/*
 * gera o número de matrícula com ano (4 dígitos) + 6 dígitos com aparência aleatória
 * - a unicidade é garantida pelo contador por Customer (nunca repete)
 * - o contador é embaralhado por uma bijeção
 * - roda na transação da aprovação: se ela falhar, o incremento é desfeito
 * */

@Component
public class RegistrationNumberGenerator {

    private final RegistrationNumberSequenceRepository registrationNumberSequenceRepository;

    private static final long MODULUS = 1_000_000L;

    // A não pode ser divisível por 2 nem por 5, senão haveria repetição
    private static final long MULTIPLIER = 738_161L;
    private static final long OFFSET = 254_983L;

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    public RegistrationNumberGenerator(RegistrationNumberSequenceRepository registrationNumberSequenceRepository) {
        this.registrationNumberSequenceRepository = registrationNumberSequenceRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String generate(UUID customerId) {
        Long sequence = registrationNumberSequenceRepository.nextValue(customerId);

        // capacidade 1.000.000 de números por Customer
        if (sequence >= MODULUS) {
            throw new IllegalStateException("Limite de números de matrícula atingido para o Customer " + customerId);
        }

        long scrambled = (MULTIPLIER * sequence + OFFSET) % MODULUS;

        return "%04d%06d".formatted(LocalDate.now(ZONE).getYear(), scrambled);
    }

}
