-- V46: 채팅 시스템 고도화 S5 — community_members 신규 테이블
-- 설계 근거: /Users/biuea/Desktop/dpdpdndn/프로젝트/채팅 시스템/20260704-채팅시스템고도화-design-db.md
--   "Detail Design > 5. community_members (신규)", 쿼리 매핑 CM1/CM2
-- community_id/user_id는 communities.id/users.id를 논리 참조 (FK 컬럼 금지 규칙에 따라 물리 FK 없음)
-- 락 영향: 신규 테이블 CREATE — 기존 테이블 무영향, 락 없음
-- 롤백: DROP TABLE community_members;

CREATE TABLE community_members
(
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'PK',
    community_id BIGINT      NOT NULL COMMENT '소속 커뮤니티 id (communities.id 참조, 물리 FK 없음)',
    user_id      BIGINT      NOT NULL COMMENT '멤버 사용자 id',
    role         VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT '역할 (HOST/MEMBER). ENUM 금지 — VARCHAR',
    status       VARCHAR(20) NOT NULL COMMENT '멤버십 상태 (PENDING_APPROVAL/ACTIVE/LEFT/KICKED). ENUM 금지 — VARCHAR',
    joined_at    DATETIME(6) NULL COMMENT 'ACTIVE 전이 시각(승인·즉시가입). PENDING이면 NULL',
    created_at   DATETIME(6) NOT NULL COMMENT '생성 시각 (UTC)',
    created_by   BIGINT      NULL COMMENT '생성자 user_id',
    updated_at   DATETIME(6) NOT NULL COMMENT '마지막 수정 시각',
    updated_by   BIGINT      NULL COMMENT '마지막 수정자 user_id',
    deleted_at   DATETIME(6) NULL COMMENT '소프트 삭제 시각',
    deleted_by   BIGINT      NULL COMMENT '삭제자 user_id',
    PRIMARY KEY (id),
    UNIQUE KEY uq_community_members_community_user (community_id, user_id, deleted_at) COMMENT '커뮤니티-유저 중복 가입 방지 + 조회 커버',
    INDEX idx_community_members_community_status (community_id, status, deleted_at)
) COMMENT '커뮤니티 멤버십';
