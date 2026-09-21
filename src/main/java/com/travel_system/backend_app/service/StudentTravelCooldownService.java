package com.travel_system.backend_app.service;

/*
* realiza o controle via debunce sob os efeitos gerados pela entrada/saida de estudantes da viagem
* envios de notificações, dados analíticos, etc
* */

import com.travel_system.backend_app.config.constants.TravelConstants;
import com.travel_system.backend_app.model.StudentTravel;
import com.travel_system.backend_app.model.Travel;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class StudentTravelCooldownService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String VIOLATION_STUDENT_KEY = "cooldown:violation:";
    private static final String BLOCKED_STUDENT_KEY = "cooldown:blocked:";
    private static final String BLOCK_COUNT_KEY = "cooldown:blockCount:";
    private static final String RECENT_TRAVEl_KEY = "recent:travel:";

    public StudentTravelCooldownService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /*
     * incrementa o contador de entradas repetidas do estudante na viagem.
     * na 1ª violação da janela, define o TTL que delimita esse período; violações
     * seguintes dentro do mesmo intervalo só incrementam, sem reiniciar a contagem.
     * quando o TTL expira, o Redis apaga a chave sozinho
     * */
    public long registerViolation(UUID travelId, UUID studentId) {
        if (travelId == null || studentId == null) {
            return 0;
        }

        String key = violationKey(studentId, travelId);

        Long count = redisTemplate.opsForValue().increment(key, 1);

        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofMillis(TravelConstants.STUDENT_TIME_FRAME));
        }

        return count == null ? 0 : count;
    }

    /*
     * bloqueia o estudante de entrar na viagem, com duração crescente a cada novo bloqueio
     * dentro de uma janela de histórico (1h). Se passar 1h sem novo bloqueio, o contador de
     * bloqueios expira sozinho e a próxima violação volta a começar do bloqueio mínimo.
     * */
    public Duration registerBlock(UUID travelId, UUID studentId) {
        String blockCountKeyStr = blockCountKey(studentId, travelId);

        Long blockCount = redisTemplate.opsForValue().increment(blockCountKeyStr, 1);

        if (blockCount != null && blockCount == 1L) {
            redisTemplate.expire(blockCountKeyStr, Duration.ofHours(1));
        }

        long escalationStep = blockCount == null ? 1L : blockCount;
        Duration blockDuration = Duration.ofMinutes(escalationStep); // 1min, 2min, 3min...

        String key = blockedKey(studentId, travelId);

        redisTemplate.opsForValue().set(key, "true", blockDuration);

        return blockDuration;
    }

    /*
     * verifica se o estudante está autorizado a entrar na viagem agora.
     * ausência da chave (nunca bloqueado, ou bloqueio já expirado) = permitido.
     * */
    public boolean isStudentAllowedToTrip(UUID travelId, UUID studentId) {
        if (travelId == null || studentId == null) {
            return false;
        }

        String key = blockedKey(studentId, travelId);

        String blocked = redisTemplate.opsForValue().get(key);

        return blocked == null;
    }

    public UUID getRecentStudentTravelId(UUID travelId, UUID studentId) {
        String value = redisTemplate.opsForValue().get(recentTravelKey(studentId, travelId));

        return value == null ? null : UUID.fromString(value);
    }

    // persiste o studante ao entrar na viagem pela primeira vez, para evitar múltiplas criações de StudentTravel para um mesmo estudante
    public void registerRecentStudentTravel(UUID travelId, UUID studentId, UUID studentTravelId) {
        redisTemplate.opsForValue().set(recentTravelKey(studentId, travelId), studentTravelId.toString(), Duration.ofMillis(TravelConstants.STUDENT_TIME_FRAME));
    }

    // calcula quantas tentativas restam antes do bloqueio, sem registrar nova violação
    public long remainingAttemptsBeforeBlock(UUID travelId, UUID studentId) {
        String key = violationKey(studentId, travelId);

        String current = redisTemplate.opsForValue().get(key);
        long currentCount = current == null ? 0 : Long.parseLong(current);

        return Math.max(0, TravelConstants.COUNT_STUDENT_ENTER_TRIP - currentCount);
    }

    // prévia da duração do próximo bloqueio, sem registrar nada (não incrementa blockCount)
    public int previewNextBlockDurationMinutes(UUID travelId, UUID studentId) {
        String key = blockCountKey(studentId, travelId);

        String current = redisTemplate.opsForValue().get(key);
        long currentBlockCount = current == null ? 0 : Long.parseLong(current);

        return (int) (currentBlockCount + 1);
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

}
