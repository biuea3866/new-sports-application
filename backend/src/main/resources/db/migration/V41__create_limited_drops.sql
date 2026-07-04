-- V38: goods domain — limited_drops table (한정판 판매 회차)
-- 설계 근거: /Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/마케팅 이벤트 고부하 대응/design-db.md "테이블 정의 > limited_drops"
-- 번호 재조정 방침: ② partner가 먼저 머지되면 V38~40 점유 → 머지 시점에 최신+1로 재배정 (design-db.md:23)
-- 롤백: DROP TABLE limited_drops; (참조 코드 미배포 시 안전 — expand-contract 1단계, 신규 가산 테이블)

CREATE TABLE limited_drops
(
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'PK',
    product_id        BIGINT      NOT NULL COMMENT '대상 goods Product id (products.id, 물리 FK 없음)',
    open_at           DATETIME(6) NOT NULL COMMENT '판매 시작 시각 (판매 시작 게이트 기준, FR-2)',
    close_at          DATETIME(6) NOT NULL COMMENT '판매 종료 시각',
    limited_quantity  INT         NOT NULL COMMENT '한정 수량 (Redis 카운터 시드 값)',
    per_user_limit    INT         NOT NULL DEFAULT 1 COMMENT '1인 구매 한도 (기본 1, FR-6). 회차별 조정',
    status            VARCHAR(20) NOT NULL COMMENT 'SCHEDULED | OPEN | SOLD_OUT | CLOSED (ENUM 금지 → VARCHAR)',
    version           BIGINT      NOT NULL DEFAULT 0 COMMENT '낙관락(@Version) — 상태 전이 동시성',
    created_at        DATETIME(6) NOT NULL COMMENT '생성 시각 (UTC)',
    created_by        BIGINT      NULL COMMENT '개설 판매자 user_id',
    updated_at        DATETIME(6) NOT NULL COMMENT '마지막 수정 시각',
    updated_by        BIGINT      NULL COMMENT '마지막 수정자 user_id',
    deleted_at        DATETIME(6) NULL COMMENT '소프트 삭제 시각 (goods 도메인 컨벤션 준수)',
    deleted_by        BIGINT      NULL COMMENT '삭제자 user_id',
    PRIMARY KEY (id),
    INDEX idx_limited_drops_product_id (product_id),
    INDEX idx_limited_drops_status_open_at (status, open_at)
) COMMENT '한정판 판매 회차 (마케팅 이벤트 고부하 대응)';
