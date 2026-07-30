-- V29: DEF-003 fix — carts user_id + deleted_at 복합 인덱스 추가
-- UNIQUE KEY uq_carts_user_id (user_id, deleted_at) 는 V12 에서 생성됨.
-- findByUserIdAndDeletedAtIsNull 쿼리(WHERE user_id = ? AND deleted_at IS NULL)
-- 최적화를 위해 user_id + deleted_at 복합 인덱스를 명시적으로 추가한다.
CREATE INDEX idx_carts_user_id_deleted_at ON carts (user_id, deleted_at);
