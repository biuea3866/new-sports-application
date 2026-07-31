-- [W1-04] 서비스별 DB 유저 + 테이블 단위 GRANT (물리 차단)
--
-- 근거: 아키텍트/20260728-msa-물리분리-실행설계.md §2-1(45테이블 소유권 배분표) §3-1(GRANT 물리 차단)
--       §3-2(허용/금지) — 소유권 배분은 이 파일에 그대로 옮긴 것이며 재판단하지 않는다.
--
-- 적용 경로 — scripts/apply-grants.sh 단일 경로 (신규 볼륨·기존 볼륨 공통):
--   docker-compose.yml 의 /docker-entrypoint-initdb.d 자동 마운트는 쓰지 않는다. 근거:
--   이 파일의 테이블 단위 GRANT 대상(products 등)은 Flyway 마이그레이션이 만드는데, Flyway는
--   backend 애플리케이션이 최초 기동할 때 실행된다 — MySQL 컨테이너의 docker-entrypoint-initdb.d
--   실행 시점(= MySQL 자체 최초 기동, backend는 아직 뜨지도 않은 시점)에는 대상 테이블이 하나도
--   없다. MySQL은 존재하지 않는 테이블에 대한 GRANT를 ERROR 1146(테이블 없음)으로 즉시 거부하고
--   mysql 클라이언트는 기본적으로 첫 에러에서 스크립트 실행을 중단하므로, 자동 마운트 시
--   docker-entrypoint-initdb.d 전체가 실패해 MySQL 컨테이너 자체가 기동하지 못하고 종료된다
--   (실제 재현 확인: `docker compose up -d mysql` → 컨테이너 Exited(1), 로그
--   "ERROR 1146 (42S02) at line 56: Table 'sports.products' doesn't exist"). 따라서 이 파일은
--   backend가 최초 마이그레이션을 마쳐 대상 테이블이 모두 생성된 뒤 scripts/apply-grants.sh 로
--   수동 적용한다 — 신규 볼륨이든 기존 볼륨이든 절차는 동일하다(경로 1종).
--
-- 멱등성: CREATE USER IF NOT EXISTS + REVOKE ALL 후 재부여. 몇 번을 재실행해도 최종 권한 상태는 동일하다.
--   REVOKE ALL PRIVILEGES, GRANT OPTION 은 대상 유저에게 권한이 하나도 없어도(최초 생성 직후) 에러 없이
--   성공한다 — 최초 적용(레코드 0건)과 재적용(레코드 N건) 양쪽에서 동일하게 동작한다.
--
-- 권한 범위: SELECT, INSERT, UPDATE, DELETE 만 부여한다 (private-be-code-convention DDL 금지 원칙과
--   동일하게, 앱 유저에는 CREATE/ALTER/DROP 권한을 주지 않는다). DDL 은 flyway_migrator 유저만 보유한다
--   (마이그레이션 전용 — 앱 런타임은 이 유저로 접속하지 않는다).
--
-- 1단계에서 앱 접속 유저는 바꾸지 않는다 (docker-compose.yml 의 DB_USERNAME 기본값 root 유지) —
--   이 파일은 root 와 별개인 신규 유저 7개만 만들고 권한을 부여할 뿐, 기존 root 권한은 건드리지 않는다.
--   따라서 GRANT 적용 후에도 기존 애플리케이션은 기존 root 접속으로 변함없이 기동·동작한다.
--
-- BATCH_* 테이블(Spring Batch 메타, §2-2 D2)은 Flyway 마이그레이션이 아니라 앱 최초 기동 시
-- BatchMetadataSchemaInitializer 가 스키마를 생성한다 — 도메인 테이블보다 늦게 생길 수 있다.
-- 위 "적용 경로"에서 확인했듯 MySQL은 존재하지 않는 테이블에 대한 GRANT를 ERROR 1146으로
-- 거부한다(테이블 존재 여부와 무관하지 않다). 따라서 이 스크립트는 BATCH_* 테이블까지 전부
-- 생성된 뒤(= backend가 최소 한 번 완전히 기동을 마친 뒤) 적용해야 한다. 그 전에 실행하면
-- BATCH_* GRANT 문에서 에러가 나며 스크립트가 중단된다 — apply-grants.sh 를 backend 최초
-- 기동 완료 확인 후 실행한다.
--
-- 롤백: 아래 DROP USER 7건으로 완전 복귀한다 (기존 접속 유저·권한 무변경 — 앱 영향 0, 스키마·데이터 변경 0건).
--   DROP USER IF EXISTS 'svc_commerce'@'%', 'svc_payment'@'%', 'svc_facility_booking'@'%',
--     'svc_social'@'%', 'svc_platform'@'%', 'svc_edge'@'%', 'flyway_migrator'@'%';
--
-- 비밀번호는 로컬/개발 전용이다 (기존 root/root, testcontainers test/test 와 동일한 수준) — 운영 반입 시
-- 별도 시크릿 관리로 교체한다 (이 티켓 범위 밖).

-- ── 1. 유저 생성 (멱등) ──
CREATE USER IF NOT EXISTS 'svc_commerce'@'%' IDENTIFIED BY 'svc_commerce_pw';
CREATE USER IF NOT EXISTS 'svc_payment'@'%' IDENTIFIED BY 'svc_payment_pw';
CREATE USER IF NOT EXISTS 'svc_facility_booking'@'%' IDENTIFIED BY 'svc_facility_booking_pw';
CREATE USER IF NOT EXISTS 'svc_social'@'%' IDENTIFIED BY 'svc_social_pw';
CREATE USER IF NOT EXISTS 'svc_platform'@'%' IDENTIFIED BY 'svc_platform_pw';
CREATE USER IF NOT EXISTS 'svc_edge'@'%' IDENTIFIED BY 'svc_edge_pw';
CREATE USER IF NOT EXISTS 'flyway_migrator'@'%' IDENTIFIED BY 'flyway_migrator_pw';

-- ── 2. 기존 권한 초기화 (재실행 시 잔여 권한 없이 아래 GRANT 만으로 최종 상태가 결정되게) ──
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'svc_commerce'@'%';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'svc_payment'@'%';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'svc_facility_booking'@'%';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'svc_social'@'%';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'svc_platform'@'%';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'svc_edge'@'%';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'flyway_migrator'@'%';

-- ── 3. 테이블 단위 GRANT — §2-1 소유권 배분표 그대로 ──

-- svc_commerce — 11 + BATCH_* 9 (Spring Batch 메타, §2-2 D2)
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.products TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.stocks TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.carts TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.cart_items TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.goods_orders TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.goods_order_items TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.limited_drops TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.events TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.seats TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.tickets TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.ticket_orders TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.BATCH_JOB_INSTANCE TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.BATCH_JOB_EXECUTION TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.BATCH_JOB_EXECUTION_PARAMS TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.BATCH_JOB_EXECUTION_CONTEXT TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.BATCH_STEP_EXECUTION TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.BATCH_STEP_EXECUTION_CONTEXT TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.BATCH_JOB_EXECUTION_SEQ TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.BATCH_STEP_EXECUTION_SEQ TO 'svc_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.BATCH_JOB_SEQ TO 'svc_commerce'@'%';

-- svc_payment — 1 (최소 권한)
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.payments TO 'svc_payment'@'%';

-- svc_facility_booking — 4
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.programs TO 'svc_facility_booking'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.regions TO 'svc_facility_booking'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.slots TO 'svc_facility_booking'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.bookings TO 'svc_facility_booking'@'%';

-- svc_social — 11
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.communities TO 'svc_social'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.community_members TO 'svc_social'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.community_bookings TO 'svc_social'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.posts TO 'svc_social'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.comments TO 'svc_social'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.rooms TO 'svc_social'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.messages TO 'svc_social'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.room_participants TO 'svc_social'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.room_invitations TO 'svc_social'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.recruitments TO 'svc_social'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.applications TO 'svc_social'@'%';

-- svc_platform — 18 (permissions 는 PH0-05 이관 후 platform 소유, §2-2 D1)
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.users TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.roles TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.user_roles TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.role_permissions TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.permissions TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.partner TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.partner_api_key TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.partner_audit_log TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.notifications TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.push_tokens TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.alerts TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.operator_inbox_notifications TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.feature_flags TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.feature_flag_audit_logs TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.mcp_tokens TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.mcp_token_scopes TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.mcp_audit_logs TO 'svc_platform'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sports.mcp_anomaly_events TO 'svc_platform'@'%';

-- svc_edge — 0 (Redis 전용: queue:* 키 + HMAC 무상태 입장 토큰). GRANT 문 없음 —
--   위 REVOKE ALL 만으로 테이블 권한 0 상태를 유지한다.

-- flyway_migrator — 전체 DDL (마이그레이션 전용, 앱 런타임 미사용). GRANT OPTION 은 주지 않는다.
GRANT ALL PRIVILEGES ON sports.* TO 'flyway_migrator'@'%';

FLUSH PRIVILEGES;
