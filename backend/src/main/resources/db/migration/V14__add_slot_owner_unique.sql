-- V14: slots 테이블 owner_id 컬럼 추가 + unique 제약 + 인덱스 — BOOKING-02

ALTER TABLE slots
    ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 0 AFTER capacity;

ALTER TABLE slots
    ADD UNIQUE KEY uq_slots_facility_date_time_range (facility_id, date, time_range, deleted_at);

CREATE INDEX idx_slots_facility_id_date_deleted_at ON slots (facility_id, date, deleted_at);
