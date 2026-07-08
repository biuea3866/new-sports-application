-- V49: 채팅 시스템 고도화 S3 — room_invitations 신규 테이블
-- 설계 근거: /Users/biuea/Desktop/dpdpdndn/프로젝트/채팅 시스템/20260704-채팅시스템고도화-design-db.md
--   "Detail Design > 3. room_invitations (신규)", 쿼리 매핑 I1/I2
-- 번호 재조정: V44가 V47~V48 재시프트에 연쇄되어 V49로 이동 (V47 참조).
-- room_id/inviter_user_id/invitee_user_id는 rooms.id/users.id를 논리 참조 (FK 컬럼 금지 규칙에 따라 물리 FK 없음)
-- 락 영향: 신규 테이블 CREATE — 기존 테이블 무영향, 락 없음
-- 롤백: DROP TABLE room_invitations;

CREATE TABLE room_invitations
(
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'PK',
    room_id         BIGINT      NOT NULL COMMENT '초대 대상 방 id (rooms.id 참조, 물리 FK 없음)',
    inviter_user_id BIGINT      NOT NULL COMMENT '초대한 사용자 id (방장)',
    invitee_user_id BIGINT      NOT NULL COMMENT '초대받은 정회원 id',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '초대 상태 (PENDING/ACCEPTED/REJECTED/REVOKED/EXPIRED). ENUM 금지 — VARCHAR',
    can_speak       TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '수락 시 부여할 발화 권한',
    expires_at      DATETIME(6) NOT NULL COMMENT '초대로 부여될 게스트 참여 만료 시각(초대 시 expiresInDays로 산정)',
    responded_at    DATETIME(6) NULL COMMENT '수락/거절/철회/만료 처리 시각. PENDING이면 NULL',
    created_at      DATETIME(6) NOT NULL COMMENT '생성 시각 (UTC)',
    created_by      BIGINT      NULL COMMENT '생성자 user_id',
    updated_at      DATETIME(6) NOT NULL COMMENT '마지막 수정 시각',
    updated_by      BIGINT      NULL COMMENT '마지막 수정자 user_id',
    deleted_at      DATETIME(6) NULL COMMENT '소프트 삭제 시각',
    deleted_by      BIGINT      NULL COMMENT '삭제자 user_id',
    PRIMARY KEY (id),
    INDEX idx_room_invitations_room_invitee_status (room_id, invitee_user_id, status, deleted_at)
) COMMENT '방 초대 (게스트 초대 상태 전이 관리)';
