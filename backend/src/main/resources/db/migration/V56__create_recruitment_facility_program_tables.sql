-- V56: 모집·시설상품·소모임예약 연동 S1 — 신규 테이블 4개
-- 설계 근거: /Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/모임·커뮤니티/20260707-모집커뮤니티연동-design-db.md
--   "MySQL 테이블 정의 1~4", "쿼리 패턴 → 인덱스 매핑 B-Q1/B-Q3/B-Q4/B-Q5"
-- 전 컬럼 FK 없음(논리 참조), ENUM 금지 → status/visibility 등 VARCHAR, BOOLEAN 없음, 시간은 DATETIME(6).
-- 락 영향: 신규 테이블 CREATE — 기존 테이블 무영향, 락 없음.
-- 배포 순서: 스키마 먼저(본 마이그레이션) → 코드(플래그 OFF: recruitment.enabled 등) → 플래그 점진 ON.
-- 롤백(역방향 DDL):
--   DROP TABLE community_bookings;
--   DROP TABLE programs;
--   DROP TABLE applications;
--   DROP TABLE recruitments;

CREATE TABLE recruitments
(
    id                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK',
    title                 VARCHAR(200)  NOT NULL COMMENT '모집 제목',
    description           VARCHAR(2000) NULL COMMENT '설명 (선택 입력)',
    capacity              INT           NOT NULL COMMENT '정원 (>=1, 도메인 검증)',
    fee_amount            DECIMAL(12,2) NOT NULL COMMENT '참가비. 0원 허용(무료 모집)',
    activity_at           DATETIME(6)   NOT NULL COMMENT '활동 일시',
    application_deadline  DATETIME(6)   NOT NULL COMMENT '신청 마감 시각. CancellationPolicy.feeRateFor 기준',
    community_id          BIGINT        NULL COMMENT '소속 community id 논리 참조. NULL=전역 모집. FK 금지',
    recruiter_user_id     BIGINT        NOT NULL COMMENT '개설자 user id 논리 참조. FK 금지',
    status                VARCHAR(20)   NOT NULL COMMENT '모집 상태 (OPEN/CLOSED/CANCELLED). ENUM 금지 — VARCHAR',
    created_at            DATETIME(6)   NOT NULL COMMENT '생성 시각 (UTC)',
    created_by            BIGINT        NULL COMMENT '생성자 user_id',
    updated_at            DATETIME(6)   NOT NULL COMMENT '마지막 수정 시각',
    updated_by            BIGINT        NULL COMMENT '마지막 수정자 user_id',
    deleted_at            DATETIME(6)   NULL COMMENT '소프트 삭제 시각',
    deleted_by            BIGINT        NULL COMMENT '삭제자 user_id',
    PRIMARY KEY (id),
    -- community_id 고카디널리티 equality 선두 → deleted_at → created_at 정렬 (B-Q3: 커뮤니티별 모집 목록)
    INDEX idx_recruitments_community_id_deleted_at_created_at (community_id, deleted_at, created_at)
) COMMENT '모집(recruitment) — 정원·마감·상태 정합이 본질이라 MySQL';

CREATE TABLE applications
(
    id                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'PK',
    recruitment_id     BIGINT      NOT NULL COMMENT '소속 recruitment id 논리 참조. FK 금지',
    applicant_user_id  BIGINT      NOT NULL COMMENT '신청자 user id 논리 참조. FK 금지',
    status             VARCHAR(20) NOT NULL COMMENT '신청 상태 (PENDING/CONFIRMED/CANCELLED/REFUNDED). ENUM 금지 — VARCHAR',
    payment_id         BIGINT      NULL COMMENT '결제 id 논리 참조. 무료 모집은 NULL',
    created_at         DATETIME(6) NOT NULL COMMENT '생성 시각 (UTC)',
    created_by         BIGINT      NULL COMMENT '생성자 user_id',
    updated_at         DATETIME(6) NOT NULL COMMENT '마지막 수정 시각',
    updated_by         BIGINT      NULL COMMENT '마지막 수정자 user_id',
    deleted_at         DATETIME(6) NULL COMMENT '소프트 삭제 시각',
    deleted_by         BIGINT      NULL COMMENT '삭제자 user_id',
    PRIMARY KEY (id),
    -- recruitment_id equality 선두 → status(IN) → deleted_at. 오버부킹 0 카운트가 이 인덱스를 타야 함 (B-Q1 hot path)
    INDEX idx_applications_recruitment_id_status_deleted_at (recruitment_id, status, deleted_at)
) COMMENT '모집 신청(application) — recruitment와 강한 관계·결제 참조·상태전이';

CREATE TABLE programs
(
    id                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK',
    facility_id        VARCHAR(255)  NOT NULL COMMENT 'Mongo facilities._id 논리 참조. slots.facility_id와 동일 타입. FK 금지',
    owner_user_id      BIGINT        NOT NULL COMMENT '시설 소유자(=program 소유자) user id 논리 참조. FK 금지',
    name               VARCHAR(200)  NOT NULL COMMENT '상품명',
    description        VARCHAR(2000) NULL COMMENT '설명 (선택 입력)',
    price              DECIMAL(12,2) NOT NULL COMMENT '가격 (>=0)',
    capacity           INT           NOT NULL COMMENT '회차 정원 (>=1)',
    duration_minutes   INT           NOT NULL COMMENT '소요 시간(분, >0)',
    created_at         DATETIME(6)   NOT NULL COMMENT '생성 시각 (UTC)',
    created_by         BIGINT        NULL COMMENT '생성자 user_id',
    updated_at         DATETIME(6)   NOT NULL COMMENT '마지막 수정 시각',
    updated_by         BIGINT        NULL COMMENT '마지막 수정자 user_id',
    deleted_at         DATETIME(6)   NULL COMMENT '소프트 삭제 시각',
    deleted_by         BIGINT        NULL COMMENT '삭제자 user_id',
    PRIMARY KEY (id),
    -- facility_id equality 선두 → deleted_at (B-Q4: 시설별 상품 목록)
    INDEX idx_programs_facility_id_deleted_at (facility_id, deleted_at)
) COMMENT '시설상품(program) — 예약·정원·가격 정합. facility_id로 Mongo 시설을 논리 참조';

CREATE TABLE community_bookings
(
    id                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'PK',
    community_id        BIGINT      NOT NULL COMMENT '커뮤니티 id 논리 참조. FK 금지',
    slot_id             BIGINT      NOT NULL COMMENT 'booking slots.id 논리 참조. FK 금지',
    linked_by_user_id   BIGINT      NOT NULL COMMENT '연결한 방장 user id 논리 참조. FK 금지',
    created_at          DATETIME(6) NOT NULL COMMENT '생성 시각 (UTC)',
    created_by          BIGINT      NULL COMMENT '생성자 user_id',
    updated_at          DATETIME(6) NOT NULL COMMENT '마지막 수정 시각',
    updated_by          BIGINT      NULL COMMENT '마지막 수정자 user_id',
    deleted_at          DATETIME(6) NULL COMMENT '소프트 삭제 시각',
    deleted_by          BIGINT      NULL COMMENT '삭제자 user_id',
    PRIMARY KEY (id),
    -- 중복 링크 멱등 가드 + community_id 접두로 목록 조회(B-Q5)까지 커버 → 별도 인덱스 불요
    UNIQUE KEY uq_community_bookings_community_slot (community_id, slot_id, deleted_at) COMMENT '커뮤니티-슬롯 중복 연결 방지 + 목록 조회 커버'
) COMMENT '소모임↔예약 연결(community_booking) — community가 slot을 소유하지 않고 링크만 보유';
