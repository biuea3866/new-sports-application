-- V50: 채팅 시스템 고도화 S4 — communities 신규 테이블
-- 설계 근거: /Users/biuea/Desktop/dpdpdndn/프로젝트/채팅 시스템/20260704-채팅시스템고도화-design-db.md
--   "Detail Design > 4. communities (신규)", 쿼리 매핑 C1/C2
-- 번호 재조정: V45가 V47~V48 재시프트에 연쇄되어 V50으로 이동 (V47 참조).
-- host_user_id는 users.id를 논리 참조 (FK 컬럼 금지 규칙에 따라 물리 FK 없음)
-- 락 영향: 신규 테이블 CREATE — 기존 테이블 무영향, 락 없음
-- 롤백: DROP TABLE communities;

CREATE TABLE communities
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
    name           VARCHAR(100) NOT NULL COMMENT '커뮤니티 이름',
    description    VARCHAR(500) NULL COMMENT '커뮤니티 설명 (선택 입력)',
    visibility     VARCHAR(20)  NOT NULL COMMENT '공개 여부 (PUBLIC/PRIVATE). ENUM 금지 — VARCHAR',
    sport_category VARCHAR(30)  NOT NULL COMMENT '스포츠 종목 카테고리. ENUM 금지 — VARCHAR',
    host_user_id   BIGINT       NOT NULL COMMENT '현재 방장 사용자 id (위임 시 갱신). FK 금지 — 일반 BIGINT',
    created_at     DATETIME(6)  NOT NULL COMMENT '생성 시각 (UTC)',
    created_by     BIGINT       NULL COMMENT '생성자 user_id',
    updated_at     DATETIME(6)  NOT NULL COMMENT '마지막 수정 시각',
    updated_by     BIGINT       NULL COMMENT '마지막 수정자 user_id',
    deleted_at     DATETIME(6)  NULL COMMENT '소프트 삭제 시각',
    deleted_by     BIGINT       NULL COMMENT '삭제자 user_id',
    PRIMARY KEY (id),
    INDEX idx_communities_visibility_deleted_at (visibility, deleted_at)
) COMMENT '커뮤니티 (스포츠 종목별 그룹)';
