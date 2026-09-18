package com.travel_system.backend_app.config.constants;

import java.time.Duration;

public class RateLimitConstants {

    public RateLimitConstants() {
        throw new UnsupportedOperationException("Não é possível instanciar uma classe de constantes");
    }

    // redis key
    public static final String RATE_LIMIT_BOOTSTRAP_KEY = "rate-limit:bootstrap:";

    // tenativas máximas dentro do intervalo de tempo
    public static final int MAX_BOOTSTRAP_ATTEMPTS  = 10;

    // intervalo de tempo permitido para as reqs
    public static Duration BOOTSTRAP_RATE_LIMIT_WINDOW = Duration.ofMinutes(15);
}
