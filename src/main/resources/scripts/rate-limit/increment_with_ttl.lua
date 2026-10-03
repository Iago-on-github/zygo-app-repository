---@diagnostic disable: undefined-global
---
-- Rate limit de janela fixa: incrementa o contador e garante a expiração de forma atômica
--
-- KEYS[1] -> chave do contador (ex.: rl:register:203.0.113.10)
-- ARGV[1] -> duração da janela em milissegundos
--
-- Retorno: { contador atual, milissegundos restantes até a janela expirar }

local key = KEYS[1]
local windowMillis = tonumber(ARGV[1])

if not windowMillis or windowMillis <= 0 then
    return redis.error_reply('ERR janela de rate limit invalida')
end

local count = redis.call('INCR', key)
local ttl = redis.call('PTTL', key)

-- -1: a chave existe sem expiração (primeira ocorrência ou chave antiga travada)
-- aplica a janela; chaves que já têm expiração mantêm o TTL atual
if ttl < 0 then
    redis.call('PEXPIRE', key, windowMillis)
    ttl = windowMillis
end

return { count, ttl }