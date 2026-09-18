package com.travel_system.backend_app.service;

import com.travel_system.backend_app.exceptions.RateLimitExceededException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;

    public RateLimitService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /*
     * Incrementa o contador da chave e aplica TTL na primeira ocorrência.
     * Deixa exceptions de infraestrutura (Redis indisponível) propagarem quem decide a política de fail-closed é o chamador.
     */
    public long incrementAndGetCount(String key, Duration window) {
        Long currentCount = redisTemplate.opsForValue().increment(key);

        if (currentCount == null) {
            throw new RateLimitExceededException("Redis retornou valor nulo ao incrementar chave de rate limit");
        }

        if (currentCount == 1L) {
            redisTemplate.expire(key, window);
        }

        return currentCount;
    }
}
