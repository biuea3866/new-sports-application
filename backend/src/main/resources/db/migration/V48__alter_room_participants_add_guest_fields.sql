-- V48: 채팅 시스템 고도화 S2 — room_participants에 게스트/읽음커서 필드 추가 (additive)
-- 설계 근거: /Users/biuea/Desktop/dpdpdndn/프로젝트/채팅 시스템/20260704-채팅시스템고도화-design-db.md
--   "Detail Design > 2. room_participants (기존 확장 — additive)", 쿼리 매핑 P1
-- 번호 재조정: V43이 #208(regions)과 중복되어 V48로 재시프트 (V47 참조).
-- participant_type/can_speak는 유효한 비즈니스 기본값(기존 참여자=정회원=발화가능)이 있으므로
-- DEFAULT를 영구 유지하는 단일 ALTER로 백필한다 (3단계 분리 불요 — design-db.md 근거).
-- 락 영향: DEFAULT 있는 NOT NULL / nullable ADD COLUMN = MySQL 8.0.12+ INSTANT(기존 행 즉시 백필, 락 없음).
--   인덱스 추가는 ALGORITHM=INPLACE, LOCK=NONE 명시.
-- idx_rp_guest_expires 컬럼 순서는 design-db.md에서 티켓 제안 순서를 조정한 것 —
--   equality(participant_type, deleted_at) 선두 + range(expires_at) 말미.
-- 롤백(역방향 DDL):
--   ALTER TABLE room_participants DROP INDEX idx_rp_guest_expires;
--   ALTER TABLE room_participants DROP COLUMN participant_type, DROP COLUMN can_speak, DROP COLUMN expires_at, DROP COLUMN last_read_message_id;

ALTER TABLE room_participants
    ADD COLUMN participant_type VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT '참여 유형 (MEMBER / GUEST). 기존 참여자는 DEFAULT로 백필. ENUM 금지 — VARCHAR' AFTER joined_at,
    ADD COLUMN can_speak TINYINT(1) NOT NULL DEFAULT 1 COMMENT '발화 권한 (1=발화 가능, 0=읽기 전용 게스트). 기존 참여자는 DEFAULT 1로 백필' AFTER participant_type,
    ADD COLUMN expires_at DATETIME(6) NULL COMMENT '게스트 참여 만료 시각. MEMBER는 NULL(무기한)' AFTER can_speak,
    ADD COLUMN last_read_message_id BIGINT NULL COMMENT '마지막으로 읽은 messages.id (읽음 커서). 미열람 시 NULL. FK 금지 — 정합은 애플리케이션(markReadUpTo)에서 보장' AFTER expires_at;

ALTER TABLE room_participants
    ADD INDEX idx_rp_guest_expires (participant_type, deleted_at, expires_at),
    ALGORITHM=INPLACE, LOCK=NONE;
