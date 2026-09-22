package com.travel_system.backend_app.service;

import com.travel_system.backend_app.config.constants.TravelConstants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.ScriptSource;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class StudentTravelCooldownService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String VIOLATION_STUDENT_KEY = "cooldown:violation:";
    private static final String BLOCKED_STUDENT_KEY = "cooldown:blocked:";
    private static final String BLOCK_COUNT_KEY = "cooldown:blockCount:";
    private static final String RECENT_TRAVEl_KEY = "recent:travel:";

    private static final long BLOCK_HISTORY_TTL_SECONDS = Duration.ofHours(1).toSeconds();

    private final RedisScript<List> precheckScript;
    private final RedisScript<List> registerScript;

    public StudentTravelCooldownService(
            RedisTemplate<String, String> redisTemplate,
            @Qualifier("cooldownPrecheckScript") RedisScript<List> precheckScript,
            @Qualifier("cooldownRegisterScript") RedisScript<List> registerScript) {
        this.redisTemplate = redisTemplate;
        this.precheckScript = precheckScript;
        this.registerScript = registerScript;
    }

    /*
     * verifica bloqueio e recupera o studentTravel recente reutilizável,
     */
    public CooldownPrecheckResult precheck(UUID travelId, UUID studentId) {
        if (travelId == null || studentId == null) {
            return new CooldownPrecheckResult(false, null);
        }

        List<String> keys = List.of(blockedKey(studentId, travelId), recentTravelKey(studentId, travelId));

        List<?> result = redisTemplate.execute(precheckScript, keys);

        boolean allowed = ((Long) result.get(0)) == 1L;
        Object recentIdRaw = result.get(1);
        UUID recentStudentTravelId = recentIdRaw == null ? null : UUID.fromString((String) recentIdRaw);

        return new CooldownPrecheckResult(allowed, recentStudentTravelId);
    }

    /*
     * chamada única, executada DEPOIS do save do StudentTravel: registra a entrada recente,
     * incrementa violação, calcula tentativas restantes e aplica bloqueio se o limiar for atingido.
     * */
    public CooldownRegisterResult registerEntry(UUID travelId, UUID studentId, UUID studentTravelId) {
        List<String> keys = List.of(
                recentTravelKey(studentId, travelId),
                violationKey(studentId, travelId),
                blockCountKey(studentId, travelId),
                blockedKey(studentId, travelId)
        );

        List<String> args = List.of(
                studentTravelId.toString(),
                String.valueOf(TravelConstants.STUDENT_TIME_FRAME),
                String.valueOf(TravelConstants.COUNT_STUDENT_ENTER_TRIP),
                String.valueOf(BLOCK_HISTORY_TTL_SECONDS)
        );

        List<?> result = redisTemplate.execute(registerScript, keys, args.toArray());

        long violations = (Long) result.get(0);
        long remaining = (Long) result.get(1);
        boolean blockedNow = ((Long) result.get(2)) == 1L;
        int blockDurationMinutes = ((Long) result.get(3)).intValue();

        return new CooldownRegisterResult(violations, remaining, blockedNow, blockDurationMinutes);
    }

    private String violationKey(UUID studentId, UUID travelId) {
        return VIOLATION_STUDENT_KEY + studentId + ":" + travelId;
    }

    private String blockedKey(UUID studentId, UUID travelId) {
        return BLOCKED_STUDENT_KEY + studentId + ":" + travelId;
    }

    private String blockCountKey(UUID studentId, UUID travelId) {
        return BLOCK_COUNT_KEY + studentId + ":" + travelId;
    }

    private String recentTravelKey(UUID studentId, UUID travelId) {
        return RECENT_TRAVEl_KEY + studentId + ":" + travelId;
    }

    public record CooldownPrecheckResult(boolean allowed, UUID recentStudentTravelId) {}

    public record CooldownRegisterResult(long violations, long remainingAttempts, boolean blockedNow, int blockDurationMinutes) {}
}