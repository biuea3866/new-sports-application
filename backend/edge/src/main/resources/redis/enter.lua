-- enter.lua
-- 가상 대기열 진입 게이트 — 신규 진입 시 고정 시퀀스(seq) 채번 + 포화 판정을 원자적으로 처리한다.
-- VirtualQueueStoreImpl(후행 티켓)이 DefaultRedisScript로 로드해 사용한다
-- (DropReservationStoreImpl의 reserve.lua 로딩 패턴을 따른다).
--
-- 계약 SSOT: backend/docs/redis/virtual-queue-keys.md, 20260709-redis-contract.md §0-1/§2.
--
-- KEYS[1] = waiting 키     queue:{type}:{id}:waiting   (ZSET, score=고정 seq)
-- KEYS[2] = heartbeat 키   queue:{type}:{id}:heartbeat (ZSET, score=now ms)
-- KEYS[3] = seq 키         queue:{type}:{id}:seq       (String, INCR 채번 + seenTotal 상한 겸용)
-- ARGV[1] = userId
-- ARGV[2] = nowEpochMs (heartbeat 초기값/갱신값)
-- ARGV[3] = maxCapacity (포화 판정 기준 — ZCARD >= maxCapacity면 거부)
-- ARGV[4] = slidingTtlMs (waiting/heartbeat/seq 슬라이딩 TTL, ms)
--
-- 반환 코드:
--   > 0  기존 시퀀스(멱등 재진입) 또는 신규 채번된 시퀀스
--   -1   포화 거부(ZCARD >= maxCapacity)
--
-- 판정 순서(고정, 순서를 바꾸지 않는다):
--   1) 기존 진입 여부 확인(ZSCORE) — 있으면 heartbeat만 갱신하고 기존 seq 반환(멱등, 재채번 없음)
--   2) 포화 판정(ZCARD >= maxCapacity) — 신규 진입만 대상
--   3) INCR seq로 고정 순번 채번 + ZADD(waiting, heartbeat) — 동시 신규 진입 경쟁에서도
--      "확인 → 채번 → ZADD"가 단일 스크립트로 원자 실행되어 시퀀스 낭비·경쟁 상태가 없다.

local userId = ARGV[1]
local now = ARGV[2]
local maxCapacity = tonumber(ARGV[3])
local slidingTtlMs = ARGV[4]

local existingSeq = redis.call('ZSCORE', KEYS[1], userId)
if existingSeq then
    redis.call('ZADD', KEYS[2], now, userId)
    redis.call('PEXPIRE', KEYS[1], slidingTtlMs)
    redis.call('PEXPIRE', KEYS[2], slidingTtlMs)
    redis.call('PEXPIRE', KEYS[3], slidingTtlMs)
    return tonumber(existingSeq)
end

local waitingSize = redis.call('ZCARD', KEYS[1])
if waitingSize >= maxCapacity then
    return -1
end

local newSeq = redis.call('INCR', KEYS[3])
redis.call('ZADD', KEYS[1], newSeq, userId)
redis.call('ZADD', KEYS[2], now, userId)
redis.call('PEXPIRE', KEYS[1], slidingTtlMs)
redis.call('PEXPIRE', KEYS[2], slidingTtlMs)
redis.call('PEXPIRE', KEYS[3], slidingTtlMs)

return newSeq
