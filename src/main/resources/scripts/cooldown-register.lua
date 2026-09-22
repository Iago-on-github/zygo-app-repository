-- combina registerRecentStudentTravel + registerViolation + remainingAttemptsBeforeBlock + previewNextBlockDurationMinutes + registerBlock (condicional) em uma única ida


-- KEYS[1] = recentTravelKey  (recent:travel:{studentId}:{travelId})
-- KEYS[2] = violationKey     (cooldown:violation:{studentId}:{travelId})
-- KEYS[3] = blockCountKey    (cooldown:blockCount:{studentId}:{travelId})
-- KEYS[4] = blockedKey       (cooldown:blocked:{studentId}:{travelId})
--
-- ARGV[1] = studentTravelId (recém-salvo, para registrar como "recente")
-- ARGV[2] = violationTtlMillis   (STUDENT_TIME_FRAME, ex: 120000)
-- ARGV[3] = violationThreshold   (COUNT_STUDENT_ENTER_TRIP, ex: 4)
-- ARGV[4] = blockCountTtlSeconds (janela de histórico de bloqueio, ex: 3600 = 1h)
--
-- Retorna: {violations, remainingAttempts, blockedNow (1/0), nextOrAppliedBlockDurationMinutes}

-- 1. marca este studentTravel como "recente" (reuso na janela de reentrada)
redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])

-- 2. incrementa a violação; define o TTL só na 1ª violação da janela
local violations = redis.call('INCR', KEYS[2])
if violations == 1 then
	redis.call('PEXPIRE', KEYS[2], ARGV[2])
end

local threshold = tonumber(ARGV[3])
local remaining = threshold - violations
if remaining < 0 then remaining = 0 end

-- 3. calcula a duração do próximo bloqueio SEM incrementar ainda (preview)
local currentBlockCount = tonumber(redis.call('GET', KEYS[3]) or '0')
local nextBlockDurationMinutes = currentBlockCount + 1

local blockedNow = 0

-- 4. se atingiu o limiar AGORA, aplica o bloqueio de fato
if violations >= threshold then
	local blockCount = redis.call('INCR', KEYS[3])
	if blockCount == 1 then
		redis.call('EXPIRE', KEYS[3], ARGV[4])
	end

	redis.call('SET', KEYS[4], 'true', 'EX', blockCount * 60)

	blockedNow = 1
	nextBlockDurationMinutes = blockCount -- reflete a duração REAL aplicada, não mais preview
end

return {violations, remaining, blockedNow, nextBlockDurationMinutes}