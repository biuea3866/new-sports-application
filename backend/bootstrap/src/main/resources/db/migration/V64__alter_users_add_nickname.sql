-- V64: users.nickname 컬럼 추가 (닉네임 도입 1단계 — DDL만)
-- 근거: 유즈케이스 캡쳐 갤러리 결함 4건(커뮤니티 게시글 `사용자 71`, 동아리 상세 `방장 #68`,
--   초대함 `초대자 #71`, 모집 신청자 `신청 #5`) — users 테이블에 사람이 읽는 이름 컬럼이 없어
--   내부 식별자가 그대로 노출됐다. 이메일 노출은 소셜 화면 개인정보 유출이라 대안이 아니다.
-- 하위 호환 판단: nullable 컬럼 추가 → 단일 마이그레이션(DDL만) 가능.
--   기존 행의 nickname 은 NULL 로 남는다 — 정상 상태다. 표시 이름은 조회 시점 폴백
--   (User.displayName → '닉네임 미설정')이 담당하므로 백필 배치 없이 배포 가능하다.
--   NOT NULL 전환은 하지 않는다(연동 대리 계정 createInactive 는 닉네임 없이 생성된다).
-- 대량 백필 DML 없음: 마이그레이션 내 UPDATE 는 users 전체 테이블 락 위험이라 넣지 않는다.
-- 유일성 제약 없음(중복 허용): 닉네임은 식별자가 아니라 표시 이름이다. UNIQUE 를 걸면
--   가입 실패·경합 처리 비용만 늘고 사칭은 막지 못한다(식별은 id 로 한다).
--   → 중복 조회 쿼리가 없으므로 인덱스도 만들지 않는다(쓰기 비용만 늘어난다).
-- 길이 20: 도메인 규칙 User.MAX_NICKNAME_LENGTH 와 동일 값. utf8mb4 기준 한글 20자.
-- 락 영향: ADD COLUMN(nullable, DEFAULT 없음) → ALGORITHM=INSTANT(메타데이터만, 테이블 재작성 없음).
--   MySQL 8.0 은 ALGORITHM=INSTANT 와 LOCK 절을 함께 쓸 수 없어(ERROR 1221) LOCK=NONE 을 생략한다.
-- 배포 순서: 스키마(본 마이그레이션) 먼저 → 코드 배포. 코드는 컬럼이 없으면 기동하지 못하므로 역순 금지.
-- 롤백(역방향 DDL): 아래를 그대로 실행한다. nullable 컬럼이라 데이터 손실 없이 안전하다.
--   ALTER TABLE users DROP COLUMN nickname;

ALTER TABLE users
    ADD COLUMN nickname VARCHAR(20) NULL COMMENT '표시 이름(닉네임). 한글·영문·숫자·밑줄 2~20자, 중복 허용. NULL 이면 미설정',
    ALGORITHM = INSTANT;
