-- admit.lua
-- 배치 admission 전진 — admitted_count를 seq(seenTotal) 상한 내에서 batchSize만큼 전진시킨다.
-- AdmissionPumpScheduler(후행 티켓)가 분산 락 획득 후 틱(2초)마다 호출한다(클러스터 단일 실행).
--
-- 계약 SSOT: 20260709-redis-contract.md §0-2/§2.
--
-- KEYS[1] = admitted_count 키  queue:{type}:{id}:admitted_count (String, 정수 고수위)
-- KEYS[2] = seq 키             queue:{type}:{id}:seq (String, seenTotal 상한 원천 — ZCARD 아님)
-- ARGV[1] = batchSize
-- ARGV[2] = slidingTtlMs
--
-- 반환: 전진 후 admitted_count. 항상 min(admitted_count+batchSize, seq) 상한을 준수한다.
--
-- seenTotal 원천이 ZCARD가 아니라 seq인 이유(§0-2): ZCARD(현재 waiting 크기)는 admission·이탈로
-- 멤버가 빠질 때마다 줄어들어 상한 자체가 활동 중에 움직인다. seq는 진입마다 INCR로만 증가하는
-- 단조 값이라 "지금까지 들어온 적 있는 총원"이라는 의도가 정확히 성립한다.

local batchSize = tonumber(ARGV[1])
local slidingTtlMs = ARGV[2]

local currentCount = tonumber(redis.call('GET', KEYS[1]) or '0')
local seenTotal = tonumber(redis.call('GET', KEYS[2]) or '0')

local target = currentCount + batchSize
if target > seenTotal then
    target = seenTotal
end

redis.call('SET', KEYS[1], target)
redis.call('PEXPIRE', KEYS[1], slidingTtlMs)

return target
