-- V61: products.seller_type 컬럼 추가 (expand-contract 1단계: 추가, DDL만)
-- 근거: [DB-01] products.seller_type 컬럼 추가 + 백필 (expand) 티켓
--   /Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/도메인 경계 재설계/tickets/DB-01-products-seller-type-expand.md
--   /Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/도메인 경계 재설계/20260708-상품주문-공유상위컨텍스트-design-db.md
-- 운영 지적 반영: 백필은 BE-11 Spring Batch 배치가 담당한다(마이그레이션 내 대량 DML 금지 —
--   UPDATE products SET seller_type=... WHERE seller_type IS NULL는 products 전체 테이블 락 위험이라 제거).
-- 하위 호환 판단: 컬럼 추가는 nullable로 시작 → 단일 마이그레이션(DDL만) 가능.
--   백필(BE-11)·NOT NULL 전환(V62, DB-02 티켓)은 별도 3단계 분리(추가 → 백필 → 제약).
--   백필 전까지 기존 행의 seller_type은 NULL로 남는다 — 정상 상태(BE-11 완료 전까지 코드가 컬럼 미참조 전제).
-- 값 도메인: B2C(일반 JWT 등록) / B2B(파트너 API Key 인증 경유 등록). ENUM 금지 → VARCHAR.
-- 인덱스: 추가 없음 — 카디널리티 2(B2C/B2B) + 잔여 술어(주 접근 경로가 아님) + 개인 프로젝트 규모.
--   근거 design-db §4 "seller_type 인덱스 판단 — 미생성" 참조.
-- 락 영향: ADD COLUMN(nullable, DEFAULT 없음) → ALGORITHM=INSTANT(메타데이터만, 테이블 재작성 없음, 무락).
--   MySQL 8.0 제약: ALGORITHM=INSTANT는 LOCK 절과 함께 명시할 수 없다(ERROR 1221 Incorrect usage of
--   ALGORITHM=INSTANT and LOCK=NONE/SHARED/EXCLUSIVE, 로컬 8.0.46 실측). INSTANT 자체가 무락을 내포하므로
--   LOCK=NONE 명시를 생략한다.
-- 배포 순서: 스키마(본 마이그레이션) 먼저 → BE-11 백필 배치 실행 → 코드 배포 → 이후 V62(NOT NULL 전환).
-- 롤백(역방향 DDL): 아래를 그대로 실행한다. nullable 컬럼이라 데이터 손실 없이 안전.
--   ALTER TABLE products DROP COLUMN seller_type;

ALTER TABLE products
    ADD COLUMN seller_type VARCHAR(10) NULL COMMENT '판매자 유형: B2C(개인/중고) / B2B(파트너/브랜드)',
    ALGORITHM = INSTANT;
