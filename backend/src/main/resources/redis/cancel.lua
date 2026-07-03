-- cancel.lua
-- reserve.lua 로 Admitted된 예약을 되돌린다 — DB `createPendingOrder` 실패 시 언더셀 방향으로 복원(TDD "실패 경로" 표).
-- DropReservationStoreImpl 이 DefaultRedisScript 로 로드해 사용한다.
--
-- KEYS[1] = remaining 키       goods:limited-drop:{dropId}:remaining
-- KEYS[2] = buyer 누적 수량 키 goods:limited-drop:{dropId}:buyer:{userId}
-- KEYS[3] = 멱등 마커 키       goods:limited-drop:{dropId}:reserved:{idempotencyKey}
-- ARGV[1] = quantity (복원할 수량 — reserve 때 차감한 값과 동일해야 한다)
--
-- 반환값: 항상 1 (복원 완료). remaining/buyer 는 언더셀 방향으로만 움직이므로 실패해도 오버셀로 이어지지 않는다.
--
-- 마커 처리 근거: 삭제한다(TTL 만료 위임 아님). cancel은 DB 트랜잭션 실패로 예약 자체가 무효화된 경우이므로,
--   같은 idempotencyKey로 재시도하는 클라이언트가 마커 TTL(예: 10분)이 만료될 때까지 AlreadyReserved 로 막히면
--   실제로는 아무 주문도 없는데 재구매가 불가능해진다. 마커를 즉시 삭제해 재시도 시 reserve.lua가 새로 판정하게 한다.
--   (반대로 confirmSuccess 는 마커를 남겨 성공 건의 멱등을 보장한다 — cancel과 confirmSuccess는 대칭이 아니다.)

local quantity = tonumber(ARGV[1])

redis.call('INCRBY', KEYS[1], quantity)

local newBuyerQuantity = redis.call('DECRBY', KEYS[2], quantity)
if newBuyerQuantity < 0 then
    redis.call('SET', KEYS[2], '0')
end

redis.call('DEL', KEYS[3])

return 1
