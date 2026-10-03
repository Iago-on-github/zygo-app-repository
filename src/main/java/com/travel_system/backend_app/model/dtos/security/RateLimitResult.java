package com.travel_system.backend_app.model.dtos.security;

import java.time.Duration;

public record RateLimitResult(
        long count,
        Duration retryAfter,
        boolean allowed
)
{
    // segundos para o cabeçalho Retry-After - arredonda para cima e nunca retorna menos que 1
    public long retryAfterSeconds() {
        long millis = retryAfter.toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }
}
