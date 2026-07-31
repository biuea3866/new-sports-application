-- evict.lua
-- 이탈(stale) 방출 — heartbeat 갱신이 cutoff 이전인 member를 maxEvictPerTick 상한 내에서
-- waiting+heartbeat에서 제거한다. AdmissionPumpScheduler(후행 티켓)가 admit.lua와 같은 틱에서 호출한다.
--
-- 계약 SSOT: 20260709-redis-contract.md §2 (maxEvictPerTick 운영 안전장치).
--
-- KEYS[1] = waiting 키    queue:{type}:{id}:waiting
-- KEYS[2] = heartbeat 키  queue:{type}:{id}:heartbeat
-- ARGV[1] = cutoffEpochMs (now - 60s)
-- ARGV[2] = maxEvictPerTick (한 틱당 방출 상한)
--
-- 반환: 방출된 member 수
--
-- maxEvictPerTick 근거: Lua 스크립트는 Redis 단일 스레드를 블로킹한다. 대규모 동시 이탈로
-- 한 틱에 수만 건이 stale 판정되면 실행 시간이 늘어나 다른 명령이 지연된다. 배치 admission과
-- 동일한 페이싱 원칙을 이탈 방출에도 적용한다 — 초과분은 heartbeat가 계속 stale 상태이므로
-- 다음 틱에 누락 없이 처리된다.

local cutoff = ARGV[1]
local maxEvictPerTick = tonumber(ARGV[2])

local staleMembers = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', cutoff, 'LIMIT', 0, maxEvictPerTick)
if #staleMembers == 0 then
    return 0
end

redis.call('ZREM', KEYS[1], unpack(staleMembers))
redis.call('ZREM', KEYS[2], unpack(staleMembers))

return #staleMembers
