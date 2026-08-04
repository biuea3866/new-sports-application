-- V65: bookings.amount 컬럼 추가 (nullable, DDL만)
-- 근거: 주문 내역(/orders) 화면에 결제 금액이 전혀 노출되지 않는 BE 계약 결함 후속 —
--   OrderHistoryItem에 amount를 추가하려면 각 주문 컨텍스트가 자기 데이터로 금액을 채워야 한다
--   (payment 테이블 역참조 금지). booking은 지금까지 자기 금액을 저장하지 않았고, 결제 금액은
--   client가 CreateBookingRequest.amount로 보낸 값을 그대로 Payment에만 저장해왔다
--   (CreateBookingUseCase#persistBookingAndPendingPayment). 이 컬럼은 그 값을 booking 자신에도
--   기록해 order 통합조회가 payment를 거치지 않고 자기 데이터로 답할 수 있게 한다.
-- 하위 호환 판단: 컬럼 추가는 nullable로 시작 → 단일 마이그레이션(DDL만) 가능. 백필 대상 없음 —
--   과거 예약은 booking 생성 시점에 amount를 저장한 적이 없어 값 자체가 존재하지 않는다(추정
--   불가). 그 예약들은 amount가 NULL로 남고, 주문 내역 화면은 null을 "금액 확정 불가"로 정직하게
--   표시(금액 줄 생략)한다 — 0으로 채우면 무료와 미확정을 구분 못해 원죄를 재생산한다.
-- 값 도메인: DECIMAL(15,2) — payments.amount(V2)·goods_orders.total_amount와 동일 정밀도.
-- 인덱스: 추가 없음 — 목록 표시용 컬럼이라 조회 조건(WHERE)에 쓰이지 않는다.
-- 락 영향: ADD COLUMN(nullable, DEFAULT 없음) → ALGORITHM=INSTANT(메타데이터만, 무락).
-- 배포 순서: 스키마(본 마이그레이션) 먼저 → BE 코드 배포(Booking.createPending이 amount를 채움).
--   과거 행 백필 없음(추정 불가한 값이라 5단계 데이터 마이그레이션 대상이 아님).
-- 롤백(역방향 DDL): 아래를 그대로 실행한다. nullable 컬럼이라 데이터 손실 없이 안전.
--   ALTER TABLE bookings DROP COLUMN amount;

ALTER TABLE bookings
    ADD COLUMN amount DECIMAL(15, 2) NULL COMMENT '예약 결제 금액 — 과거 예약은 저장 이력이 없어 NULL(금액 확정 불가)',
    ALGORITHM = INSTANT;
