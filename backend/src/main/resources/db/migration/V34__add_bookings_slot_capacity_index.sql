-- DB-01 (결함#2): bookings 활성예약 capacity 카운트 쿼리 인덱스 보강
-- countBySlotIdAndStatusIn(slotId, [PENDING, CONFIRMED]) 쿼리가 인덱스를 타도록 보장한다.
-- capacity>1 가능 → 부분 unique 인덱스 미적용. 동시성 최종 방어선은 BE-06b slot row 비관락.
CREATE INDEX idx_bookings_slot_id_status ON bookings (slot_id, status);
