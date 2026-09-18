package com.travel_system.backend_app.config.constants;

import java.time.Duration;

public class SensitiveOperationConstants {

    public SensitiveOperationConstants() {
        throw new UnsupportedOperationException("Não é possível instanciar uma classe de constantes");
    }

    // tempo p/ cache temporário de auth para operações sensíveis no sistema
    public static final Duration TEMPORARY_AUTH_TTL = Duration.ofMinutes(5);

    // tempo que valida se o link do email ainda é válido e pode ser clicado
    public static final Duration EXPIRES_SENSITIVE_ENTITY_TTL = Duration.ofMinutes(10);
}
