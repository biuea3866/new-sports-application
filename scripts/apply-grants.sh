#!/usr/bin/env bash
# [W1-04] 서비스별 DB 유저 + 테이블 단위 GRANT — 수동 사후 적용 (재실행 가능/멱등).
#
# 신규 볼륨·기존 볼륨 공통으로 이 스크립트가 유일한 적용 경로다. docker-entrypoint-initdb.d
# 자동 마운트는 쓰지 않는다 — GRANT 대상 테이블은 Flyway 마이그레이션(backend 최초 기동 시
# 실행)이 만들므로, MySQL 컨테이너 자체의 최초 기동 시점(= initdb 스크립트 실행 시점)에는
# 아직 존재하지 않아 GRANT가 ERROR 1146으로 실패하고 MySQL 컨테이너가 기동 자체를 못 한다
# (근거는 infra/mysql/grants/02-grants.sql 헤더 주석 참조, 실제 재현 확인됨).
#
# 전제 조건: backend가 최소 1회 완전히 기동해 Flyway 마이그레이션 + Spring Batch 메타 테이블
#   (BATCH_*) 생성을 마친 뒤 실행한다. 그 전에 실행하면 대상 테이블이 없어 에러가 난다.
#
# 멱등: CREATE USER IF NOT EXISTS + REVOKE ALL 후 재부여 방식이라 몇 번을 재실행해도 최종 권한
#       상태는 동일하다.
#
# 사용: scripts/apply-grants.sh  (backend 최초 기동 완료 확인 후 1회)
# 환경변수:
#   MYSQL_CONTAINER    - 대상 mysql 컨테이너명 (기본: sports-dev-mysql-1)
#   MYSQL_ROOT_PASSWORD - root 비밀번호 (기본: root, docker-compose.yml 기본값과 동일)
#
# 롤백: DROP USER IF EXISTS 'svc_commerce'@'%', 'svc_payment'@'%', 'svc_facility_booking'@'%',
#       'svc_social'@'%', 'svc_platform'@'%', 'svc_edge'@'%', 'flyway_migrator'@'%';
#       (기존 접속 유저·권한 무변경이라 앱 영향 0, 스키마·데이터 변경 0건)
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MYSQL_C="${MYSQL_CONTAINER:-sports-dev-mysql-1}"
ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"
GRANTS_SQL="${DIR}/../infra/mysql/grants/02-grants.sql"

if [[ ! -f "$GRANTS_SQL" ]]; then
  echo "✗ GRANT SQL 파일을 찾을 수 없습니다: $GRANTS_SQL" >&2
  exit 1
fi

echo "▶ 서비스별 DB 유저 + GRANT 적용 (${MYSQL_C})…"
docker exec -i "$MYSQL_C" mysql --default-character-set=utf8mb4 -uroot -p"${ROOT_PASSWORD}" \
  < "$GRANTS_SQL"

echo "✔ GRANT 적용 완료 (멱등 — 재실행 안전)"
echo "  롤백: DROP USER IF EXISTS 'svc_commerce'@'%', 'svc_payment'@'%', 'svc_facility_booking'@'%', 'svc_social'@'%', 'svc_platform'@'%', 'svc_edge'@'%', 'flyway_migrator'@'%';"
