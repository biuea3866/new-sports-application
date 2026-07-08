-- INFRA-08 reseed 배치 — 10분 주기 synthetic 소진성 자원 멱등 복원
-- 근거 티켓: 프로젝트/스포츠앱/상시 트래픽 시뮬레이터/Tickets/INFRA-08-reseed-배치-멱등복원.md
-- 근거 TDD: 프로젝트/스포츠앱/상시 트래픽 시뮬레이터/TDD.md "실패 경로·동시성·멱등"(ADR-002)
--
-- 목적: qa/load/seeds/simulator-baseline.sql(INFRA-03)이 만든 synthetic 행의 "소비된" 필드만
--       baseline 절대값으로 되돌린다. 행 자체를 만들거나 지우지 않는다(그건 provision의 책임) —
--       reseed는 오직 "소진(consume)"을 "복원(restore)"할 뿐이다.
--
-- synthetic 범위 (simulator-baseline.sql·provision.sh와 동일 계약, 드리프트 시 두 파일 동시 갱신):
--   - 유저(X-User-Id): 900000 ~ 999999
--   - 슬롯(booking):   slots.id 9000001 ~ 9000030 (capacity 1,000,000, 값 자체는 소비되지 않아 미복원)
--   - 상품/재고(goods): products.id 9010001 ~ 9010010 baseline quantity=1,000,000(B2C 쓰기 곡선 공용 풀)
--                       products.id 9010011           baseline quantity=200,000,000(마케팅 drop 전용, INFRA-03 Step 2-1)
--   - 이벤트/좌석(ticketing): events.id 9000001, seats.id 9000001 ~ 9005000
--
-- 복원 대상과 근거 (TDD ADR-002 "관측 왜곡 금지" — 앱 API를 호출하지 않고 DB에 직접 절대값 upsert):
--   1. 슬롯 가용 = capacity - count(bookings.status IN (PENDING,CONFIRMED)) 이므로(BookingDomainService#doBooking),
--      슬롯 자체가 아니라 "활성 예약"을 CANCELLED로 되돌려 가용량을 복원한다.
--   2. 재고는 stocks.quantity를 절대값으로 직접 복원한다(GoodsDomainService#restoreStock과 동일한
--      "재고 원복" 의미를 SQL 절대값 upsert로 구현 — 가산이 아니라 절대값이라 드리프트가 없다).
--   3. 좌석 가용은 Redis 락(seat:lock:{eventId}:{seatId}) 유무로 판정되지만(TicketingDomainService
--      #getSeatsWithAvailability), 이미 확정된 좌석은 tickets.status=ISSUED가 uk_tickets_active_seat
--      (활성 좌석 유니크)로 재판매를 막는다 — 이 유니크를 해제하려면 티켓을 REVOKED로 되돌려야 한다.
--      (Redis seat:lock:* 키 삭제는 SQL이 다룰 수 없어 reseed.sh가 redis-cli로 별도 수행한다.)
--
-- 청크·jitter (NFR "재충전이 관측 대상에 순간 write 스파이크를 유발 금지"):
--   - bookings(최대 30행)·stocks(11행)는 한 번의 write로도 순간 부하가 무의미할 만큼 소규모라
--     청크를 나누지 않는다(각 1 UPDATE). 근거를 남기는 이유는 "왜 청크가 없는가"도 설계 결정이기 때문.
--   - tickets(최대 5,000행)는 유일하게 청크가 의미 있는 규모라 seat_id 1,000 단위 5개 청크로 나누고
--     청크 사이 SELECT SLEEP(1 ~ 5초 랜덤)을 둬 10분 윈도 안에서 write를 시간적으로 흩뿌린다.
--
-- 멱등성: 전부 절대값 upsert/상태 전이이며, 이미 목표 상태인 행은 WHERE 조건에 걸리지 않아
--         2회 연속 실행해도 추가 부작용이 없다(드리프트 없음 — private-db-schema-convention 하위 호환 원칙과 동일 정신).
--
-- 부분 실패 시: 이 파일은 단일 트랜잭션으로 묶지 않는다 — 한 스텝이 실패해도(예: 락 대기 타임아웃)
--   나머지 스텝은 계속 진행하고, 다음 10분 주기 재실행이 실패한 스텝을 마저 수렴시킨다
--   (TDD "실패 경로" 표 "reseed 부분 실패" 항목 — 절대 복원이라 재실행이 곧 재시도).
--
-- 실사용자 데이터 미접촉 보장: 모든 UPDATE의 WHERE 절이 synthetic PK 범위(slot_id/seat_id/product_id)
--   **그리고** synthetic 소유자·사용자 범위(user_id, owner_id)를 동시에 요구한다(이중 스코프) —
--   두 조건 중 하나만 실제로 참인 실사용자 행은 걸러진다.

-- ============================================================
-- Step 1: 슬롯 가용 복원 — synthetic 예약 취소 (booking 도메인)
--   PENDING/CONFIRMED → CANCELLED (BookingStatus.canTransitTo 규칙과 동일한 전이만 사용)
-- ============================================================
UPDATE bookings
SET status     = 'CANCELLED',
    updated_at = NOW(6)
WHERE slot_id BETWEEN 9000001 AND 9000030
  AND user_id BETWEEN 900000 AND 999999
  AND status IN ('PENDING', 'CONFIRMED')
  AND deleted_at IS NULL;

-- ============================================================
-- Step 2: 재고 절대 복원 (goods 도메인) — 청크 불필요(11행)
-- ============================================================
UPDATE stocks
SET quantity   = 1000000,
    updated_at = NOW(6)
WHERE product_id BETWEEN 9010001 AND 9010010
  AND deleted_at IS NULL
  AND quantity <> 1000000;

UPDATE stocks
SET quantity   = 200000000,
    updated_at = NOW(6)
WHERE product_id = 9010011
  AND deleted_at IS NULL
  AND quantity <> 200000000;

-- ============================================================
-- Step 3: 좌석 가용 복원 — synthetic ISSUED 티켓 revoke (ticketing 도메인)
--   active_seat_id는 생성 컬럼(GENERATED ALWAYS, status='ISSUED'일 때만 seat_id 값)이라
--   status를 REVOKED로 바꾸는 즉시 uk_tickets_active_seat 유니크가 자동 해제된다.
--   ticket_orders 조인으로 synthetic user_id(900000+) 소유 티켓만 대상으로 이중 스코프.
--   5,000행 규모라 seat_id 1,000 단위 5개 청크 + 청크 사이 SLEEP(1~5초 랜덤 jitter)로 분산한다.
-- ============================================================

-- 청크 1/5: seat_id 9000001 ~ 9001000
UPDATE tickets t
    JOIN ticket_orders o ON o.id = t.ticket_order_id
SET t.status     = 'REVOKED',
    t.deleted_at = NOW(6),
    t.updated_at = NOW(6)
WHERE t.seat_id BETWEEN 9000001 AND 9001000
  AND o.user_id BETWEEN 900000 AND 999999
  AND t.status = 'ISSUED'
  AND t.deleted_at IS NULL;

SELECT SLEEP(1 + RAND() * 4);

-- 청크 2/5: seat_id 9001001 ~ 9002000
UPDATE tickets t
    JOIN ticket_orders o ON o.id = t.ticket_order_id
SET t.status     = 'REVOKED',
    t.deleted_at = NOW(6),
    t.updated_at = NOW(6)
WHERE t.seat_id BETWEEN 9001001 AND 9002000
  AND o.user_id BETWEEN 900000 AND 999999
  AND t.status = 'ISSUED'
  AND t.deleted_at IS NULL;

SELECT SLEEP(1 + RAND() * 4);

-- 청크 3/5: seat_id 9002001 ~ 9003000
UPDATE tickets t
    JOIN ticket_orders o ON o.id = t.ticket_order_id
SET t.status     = 'REVOKED',
    t.deleted_at = NOW(6),
    t.updated_at = NOW(6)
WHERE t.seat_id BETWEEN 9002001 AND 9003000
  AND o.user_id BETWEEN 900000 AND 999999
  AND t.status = 'ISSUED'
  AND t.deleted_at IS NULL;

SELECT SLEEP(1 + RAND() * 4);

-- 청크 4/5: seat_id 9003001 ~ 9004000
UPDATE tickets t
    JOIN ticket_orders o ON o.id = t.ticket_order_id
SET t.status     = 'REVOKED',
    t.deleted_at = NOW(6),
    t.updated_at = NOW(6)
WHERE t.seat_id BETWEEN 9003001 AND 9004000
  AND o.user_id BETWEEN 900000 AND 999999
  AND t.status = 'ISSUED'
  AND t.deleted_at IS NULL;

SELECT SLEEP(1 + RAND() * 4);

-- 청크 5/5: seat_id 9004001 ~ 9005000
UPDATE tickets t
    JOIN ticket_orders o ON o.id = t.ticket_order_id
SET t.status     = 'REVOKED',
    t.deleted_at = NOW(6),
    t.updated_at = NOW(6)
WHERE t.seat_id BETWEEN 9004001 AND 9005000
  AND o.user_id BETWEEN 900000 AND 999999
  AND t.status = 'ISSUED'
  AND t.deleted_at IS NULL;

-- 검증 쿼리 (수동 확인용 — reseed.sh가 자동 실행하지 않음)
-- SELECT COUNT(*) FROM bookings WHERE slot_id BETWEEN 9000001 AND 9000030 AND status IN ('PENDING','CONFIRMED');  -- 0
-- SELECT quantity FROM stocks WHERE product_id BETWEEN 9010001 AND 9010010;                                       -- 전부 1000000
-- SELECT quantity FROM stocks WHERE product_id = 9010011;                                                         -- 200000000
-- SELECT COUNT(*) FROM tickets WHERE seat_id BETWEEN 9000001 AND 9005000 AND status = 'ISSUED' AND deleted_at IS NULL; -- 0
