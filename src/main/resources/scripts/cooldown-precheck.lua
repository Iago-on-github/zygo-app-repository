-- pré-checagem (antes do save no postgres) vai combinar isStudentAllowedToTrip + getRecentStudentTravelId em uma única ida

-- KEYS[1] = blockedKey       (cooldown:blocked:{studentId}:{travelId})
-- KEYS[2] = recentTravelKey  (recent:travel:{studentId}:{travelId})
--
-- Sem ARGV: são só leituras.
-- Retorna: {allowed (1/0), recentStudentTravelId (string ou nil)}

local blocked = redis.call('GET', KEYS[1])
if blocked then
	return {0, false}
end

local recentTravelId = redis.call('GET', KEYS[2])
if recentTravelId then
	return {1, recentTravelId}
else
	return {1, false}
end