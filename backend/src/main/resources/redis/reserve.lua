-- reserve.lua
-- 한정판 입장 게이트(Admission gate) — TDD "마케팅 이벤트 고부하 대응" ADR-003 / BE-04 계약.
-- DropReservationStoreImpl 이 DefaultRedisScript 로 로드해 사용한다 (SeatLockStoreImpl 의 UNLOCK_LUA 선례를 별도 파일로 분리).
--
-- KEYS[1] = remaining 키           goods:limited-drop:{dropId}:remaining
-- KEYS[2] = buyer 누적 수량 키     goods:limited-drop:{dropId}:buyer:{userId}
-- KEYS[3] = 멱등 마커 키           goods:limited-drop:{dropId}:reserved:{idempotencyKey}
-- ARGV[1] = quantity (예약 수량)
-- ARGV[2] = perUserLimit (1인 누적 한도)
-- ARGV[3] = markerTtl (멱등 마커 TTL, 초)
--
-- 반환 코드:
--   1 = Admitted             (remaining/buyer 차감·마커 세팅 완료)
--   0 = SoldOut               (remaining 부족 — DB 미도달, FR-8)
--   2 = AlreadyReserved       (동일 idempotencyKey 재시도 — 재차감 없음)
--   3 = PerUserLimitExceeded  (1인 누적 한도 초과 — FR-6)
--
-- 판정 순서(ADR-003 고정, 순서를 바꾸지 않는다):
--   1) 멱등 마커 확인   — 가장 저렴한 조회로 중복 재처리를 먼저 차단
--   2) 1인 한도 확인     — 게이트 통과 전에 사용자별 초과를 걸러 remaining 소모를 막는다
--   3) 소진 판정(decr-if-positive) — 원자적으로 정확히 remaining 만큼만 Admitted
--   4) DECRBY remaining + INCRBY buyer + buyer TTL을 remaining과 정렬 + SET 마커 — 판정 통과 시에만 상태 변경 (단일 스크립트라 원자적)
--
-- buyer TTL 정렬 근거(인프라 리뷰 p1): INCRBY로 buyer 키가 최초 생성될 때 TTL이 없으면 영구 키가 되어
--   noeviction+maxmemory 환경에서 회차마다 (dropId,userId) 키가 누적돼 OOM으로 이어진다.
--   INCRBY 직후 remaining의 PTTL을 그대로 buyer에 PEXPIRE 해 "buyer TTL = 회차 수명과 동일" 계약을 스크립트로 강제한다.

if redis.call('EXISTS', KEYS[3]) == 1 then
    return 2
end

local quantity = tonumber(ARGV[1])
local perUserLimit = tonumber(ARGV[2])
local markerTtl = tonumber(ARGV[3])

local currentBuyerQuantity = tonumber(redis.call('GET', KEYS[2]) or '0')
if currentBuyerQuantity + quantity > perUserLimit then
    return 3
end

local remaining = tonumber(redis.call('GET', KEYS[1]) or '-1')
if remaining < quantity then
    return 0
end

redis.call('DECRBY', KEYS[1], quantity)
redis.call('INCRBY', KEYS[2], quantity)

local remainingPttl = redis.call('PTTL', KEYS[1])
if remainingPttl > 0 then
    redis.call('PEXPIRE', KEYS[2], remainingPttl)
end

redis.call('SET', KEYS[3], '1', 'EX', markerTtl)

return 1
