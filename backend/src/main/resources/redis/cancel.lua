-- cancel.lua
-- reserve.lua 로 Admitted된 예약을 되돌린다 — DB `createPendingOrder` 실패 시 언더셀 방향으로 복원(TDD "실패 경로" 표).
-- DropReservationStoreImpl 이 DefaultRedisScript 로 로드해 사용한다.
--
-- KEYS[1] = remaining 키       goods:limited-drop:{dropId}:remaining
-- KEYS[2] = buyer 누적 수량 키 goods:limited-drop:{dropId}:buyer:{userId}
-- KEYS[3] = 멱등 마커 키       goods:limited-drop:{dropId}:reserved:{idempotencyKey}
-- ARGV[1] = quantity (복원할 수량 — reserve 때 차감한 값과 동일해야 한다)
--
-- 반환 코드:
--   1 = Restored   (마커 존재 확인 후 remaining/buyer 복원 + 마커 삭제)
--   0 = NoOp       (마커가 이미 없음 — 이미 취소됐거나 애초에 예약된 적이 없는 idempotencyKey. 상태 변경 없음)
--
-- 멱등 가드 근거(인프라 리뷰 p2): 마커 존재를 확인하지 않고 무조건 INCRBY 하면, 동일 idempotencyKey로
--   cancel이 2회 유입될 때(네트워크 재시도 등) remaining이 reserve로 차감된 적 없는데도 두 번 복원되어
--   seed 수량을 초과하는 "과복원"(오버셀 방향)이 발생한다. 마커(KEYS[3])가 있을 때만 복원을 수행해
--   cancel을 멱등하게 만든다 — remaining/buyer는 항상 언더셀 방향으로만 움직인다.
--
-- 마커 처리 근거: 복원을 수행하는 경우에만 삭제한다(TTL 만료 위임 아님). cancel은 DB 트랜잭션 실패로 예약
--   자체가 무효화된 경우이므로, 같은 idempotencyKey로 재시도하는 클라이언트가 마커 TTL(예: 10분)이 만료될
--   때까지 AlreadyReserved 로 막히면 실제로는 아무 주문도 없는데 재구매가 불가능해진다. 마커를 즉시 삭제해
--   재시도 시 reserve.lua가 새로 판정하게 한다.
--   (반대로 confirmSuccess 는 마커를 남겨 성공 건의 멱등을 보장한다 — cancel과 confirmSuccess는 대칭이 아니다.)
--
-- buyer TTL 정렬 근거(인프라 리뷰 p1과 동일 계약): DECRBY 후 음수 방지를 위해 buyer를 SET '0'으로
--   덮어쓰면 TTL이 사라진다(SET은 기존 TTL을 유지하지 않는다). 복원 마지막 단계에서 remaining의 PTTL을
--   buyer에 다시 PEXPIRE 해 "buyer TTL = 회차 수명과 동일" 계약을 어떤 경로로도 깨지지 않게 한다.

if redis.call('EXISTS', KEYS[3]) == 0 then
    return 0
end

local quantity = tonumber(ARGV[1])

redis.call('INCRBY', KEYS[1], quantity)

local newBuyerQuantity = redis.call('DECRBY', KEYS[2], quantity)
if newBuyerQuantity < 0 then
    redis.call('SET', KEYS[2], '0')
end

local remainingPttl = redis.call('PTTL', KEYS[1])
if remainingPttl > 0 then
    redis.call('PEXPIRE', KEYS[2], remainingPttl)
end

redis.call('DEL', KEYS[3])

return 1
