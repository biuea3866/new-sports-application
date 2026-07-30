-- V62: products.seller_type NOT NULL 전환 (expand-contract 2단계: contract, DDL만)
-- 근거: [DB-02] products.seller_type NOT NULL 전환 (contract) 티켓
--   /Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/도메인 경계 재설계/tickets/DB-02-products-seller-type-not-null.md
--   V61(DB-01)이 nullable로 컬럼을 추가(expand)했고, 이 마이그레이션이 그 마무리(contract)다.
--
-- ⚠️ 선행 게이트 (모두 충족 전 이 마이그레이션 적용 금지 — 운영 적용 시점 구속, 코드 의존 아님):
--   1. BE-03 듀얼라이트 배포 완료 (신규 Product 등록 시 seller_type 값이 채워짐)
--   2. BE-11 Spring Batch 배치 백필 완료 (기존 NULL 행을 B2C로 채움)
--   3. 백필 검증 통과: SELECT COUNT(*) FROM products WHERE seller_type IS NULL; -- 결과 0
--   4. 기능 배포 완료 (catalog/order 파사드 BE-07/08 + SecurityConfig BE-09)
--   위 게이트 미충족 상태에서 이 파일을 적용하면 ERROR 1138 (22004) Invalid use of NULL value로
--   실패한다 (로컬 재현 완료 — NULL 잔존 1건 상태에서 적용 시 동일 에러 확인).
--   실패 시 대응: 마이그레이션을 되돌리지 말고, BE-11 배치를 먼저 완료 후 재적용한다.
--
-- 락 영향: NULL 허용 → NOT NULL 속성 변경은 MySQL 8.0 Online DDL에서 테이블 재작성(rebuild)을
--   동반한다(MySQL 공식 Online DDL 표 "Changing NULL/NOT NULL attribute" = Rebuilds Table: Yes,
--   INNODB_TABLES.TABLE_ID 변경으로 실측 확인). 단 ALGORITHM=INPLACE, LOCK=NONE으로 재작성 중에도
--   동시 DML(읽기·쓰기)을 허용하므로 서비스 쓰기를 막지 않는다 (로컬 8.0.46 실측: NULL=0 상태에서
--   exit 0, 컬럼 즉시 NOT NULL로 전환 확인. NULL 잔존 상태에서는 ALGORITHM/LOCK 문법은 수락되고
--   데이터 검증 단계에서 ERROR 1138로 안전 실패 — 게이트 미충족 시 이 마이그레이션이 실패한다).
--   재작성 I/O 비용은 테이블 크기에 비례하나 개인 프로젝트 규모(products 소규모)라 무시 가능한 수준.
--   ※ 대형 테이블에 이 패턴을 재사용할 때는 재작성 비용(크기 비례 I/O)을 반드시 고려할 것.
--
-- 대량 백필 DML 금지: 이 파일에는 UPDATE 문을 포함하지 않는다. 백필은 BE-11 Spring Batch가
--   청크 단위로 이미 완료했다는 전제다. Flyway는 DDL 전용.
--
-- 배포 순서: BE-11 배치 실행 → NULL=0 검증 → 기능 배포(BE-07/08/09) → 배포 후 검증 → 이 마이그레이션 적용.
--
-- 롤백(역방향 DDL): 제약을 완화하는 것이라 데이터 손실 없이 안전하게 되돌릴 수 있다.
--   ALTER TABLE products
--       MODIFY COLUMN seller_type VARCHAR(10) NULL COMMENT '판매자 유형: B2C(개인/중고) / B2B(파트너/브랜드)',
--       ALGORITHM = INPLACE, LOCK = NONE;

ALTER TABLE products
    MODIFY COLUMN seller_type VARCHAR(10) NOT NULL COMMENT '판매자 유형: B2C(개인/중고) / B2B(파트너/브랜드)',
    ALGORITHM = INPLACE, LOCK = NONE;
