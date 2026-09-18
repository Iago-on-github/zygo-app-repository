package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.constants.CacheConstants;
import com.travel_system.backend_app.config.constants.SensitiveOperationConstants;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class RedisSetupAuthenticationService {
    private final RedisTemplate<String, String> redisTemplate;

    public RedisSetupAuthenticationService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void putTemporarySetupSensitiveOperation(String userEmail) {
        if (userEmail == null) return;

        // constant ttl para operações sensíveis
        Duration temporaryAuthTtl = SensitiveOperationConstants.TEMPORARY_AUTH_TTL;

        String key = CacheConstants.SETUP_AUTH_PLATFORM_ADMIN_KEY + userEmail;

        redisTemplate.opsForValue().set(key, "AUTHORIZED", temporaryAuthTtl);
    }

    public boolean isTemporarySensitiveOperationAuthorized(String userEmail) {
        if (userEmail == null) return false;

        String key = CacheConstants.SETUP_AUTH_PLATFORM_ADMIN_KEY + userEmail;

        return "AUTHORIZED".equals(redisTemplate.opsForValue().get(key));
    }
}
