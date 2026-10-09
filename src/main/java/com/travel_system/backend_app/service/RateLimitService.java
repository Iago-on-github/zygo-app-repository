package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.RateLimitServiceUnavailableException;
import com.travel_system.backend_app.model.dtos.security.RateLimitResult;
import com.travel_system.backend_app.model.enums.RateLimitPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class RateLimitService {
    private final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private static final String KEY_NAMESPACE = "rl:";
    private static final String SCRIPT_PATH = "scripts/rate-limit/increment-with-ttl.lua";

    private final StringRedisTemplate redisTemplate;

    @SuppressWarnings("rawtypes")
    private final RedisScript<List> incrementWithTtlScript;

    public RateLimitService(StringRedisTemplate redisTemplate,
                            @Qualifier("incrementWithTtlScript")  RedisScript<List> incrementWithTtlScript) {
        this.redisTemplate = redisTemplate;
        this.incrementWithTtlScript = incrementWithTtlScript;
    }

    /*
     * Registra uma tentativa para a política e o identificador (IP ou usuário) e informa se ela está dentro do limite.
     * Falhas de infraestrutura (Redis indisponível, resposta inválida) propagam como RateLimitServiceUnavailableException:
     * quem decide entre bloquear ou liberar nesses casos é o chamador.
     */
    public RateLimitResult consume(RateLimitPolicy policy, String identifier) {
        if (policy == null) {
            throw new IllegalArgumentException("A política de rate limit é obrigatória");
        }
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("O identificador de rate limit é obrigatório");
        }

        String key = KEY_NAMESPACE + policy.keyPrefix() + identifier;
        String windowMillis = String.valueOf(policy.window().toMillis());

        List<?> result;
        try {
            result = redisTemplate.execute(incrementWithTtlScript, List.of(key), windowMillis);
        } catch (Exception e) {
            log.error("[rate-limit] Falha ao executar o script. key={}", key, e);
            throw new RateLimitServiceUnavailableException("Falha ao consultar o serviço de rate limiting", e);
        }

        if (result == null || result.size() < 2
                || !(result.get(0) instanceof Number count)
                || !(result.get(1) instanceof Number ttlMillis)) {
            throw new RateLimitServiceUnavailableException("Resposta inválida do Redis para a chave de rate limit", null);
        }

        long currentCount = count.longValue();
        boolean allowed = currentCount <= policy.maxAttempts();

        return new RateLimitResult(currentCount, Duration.ofMillis(ttlMillis.longValue()), allowed);
    }

    @SuppressWarnings("rawtypes")
    private static RedisScript<List> loadIncrementWithTtlScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(SCRIPT_PATH)));
        script.setResultType(List.class);
        return script;
    }
}
