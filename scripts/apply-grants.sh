#!/usr/bin/env bash
# [W1-04] 서비스별 DB 유저 + 테이블 단위 GRANT — 수동 재적용 경로 (재실행 가능/멱등).
#
# 1차 적용 경로는 docker-compose.yml 의 `db-grants` 원샷 사이드카다(backend healthy 이후 자동
# 실행, 리뷰 p2① 후속). 이 스크립트는 사이드카를 쓰지 않는 상황(사이드카 없이 띄운 기존 스택,
# 수동 재적용이 필요한 경우)의 대체 경로다.
#
# docker-entrypoint-initdb.d 자동 마운트는 쓰지 않는다 — GRANT 대상 테이블은 Flyway
# 마이그레이션(backend 최초 기동 시 실행)이 만들므로, MySQL 컨테이너 자체의 최초 기동 시점(=
# initdb 스크립트 실행 시점)에는 아직 존재하지 않아 GRANT가 ERROR 1146으로 실패하고 MySQL
# 컨테이너가 기동 자체를 못 한다(근거는 infra/mysql/grants/02-grants.sql 헤더 주석 참조, 실제
# 재현 확인됨).
#
# 전제 조건: backend가 최소 1회 완전히 기동해 Flyway 마이그레이션 + Spring Batch 메타 테이블
#   (BATCH_*) 생성을 마친 뒤 실행한다. 그 전에 실행하면 대상 테이블이 없어 에러가 난다.
#
# 멱등: CREATE USER IF NOT EXISTS + REVOKE ALL 후 재부여 방식이라 몇 번을 재실행해도 최종 권한
#       상태는 동일하다.
#
# 사용법:
#   scripts/apply-grants.sh          # 적용 + 적용 직후 권한 개수 검증
#   scripts/apply-grants.sh verify   # 적용 없이 현재 권한 개수만 검증(드리프트 확인용)
#
# 환경변수:
#   MYSQL_CONTAINER    - 대상 mysql 컨테이너명 (기본: sports-dev-mysql-1)
#   MYSQL_ROOT_PASSWORD - root 비밀번호 (기본: root, docker-compose.yml 기본값과 동일)
#   ALLOW_PROD         - 대상 컨테이너명에 "prod"가 포함될 때만 확인하는 안전장치. 1이 아니면
#                        prod로 보이는 대상에 대해 즉시 거부한다(아래 "prod 호출" 참고).
#
# prod 호출 예시 (컨테이너명·root 비밀번호는 dev와 다르다 — `docker compose -p sports-prod` 스택):
#   ALLOW_PROD=1 MYSQL_CONTAINER=sports-prod-mysql-1 \
#     MYSQL_ROOT_PASSWORD=<prod 시크릿> scripts/apply-grants.sh
#
# 롤백: DROP USER IF EXISTS 'svc_commerce'@'%', 'svc_payment'@'%', 'svc_facility_booking'@'%',
#       'svc_social'@'%', 'svc_platform'@'%', 'svc_edge'@'%', 'flyway_migrator'@'%';
#       (기존 접속 유저·권한 무변경이라 앱 영향 0, 스키마·데이터 변경 0건)
set -euo pipefail

trap 'echo "✗ 스크립트가 중단됐습니다 — REVOKE만 적용되고 GRANT가 덜 반영된 상태일 수 있습니다. \
이 스크립트는 멱등이므로 그대로 재실행하면 최종 권한 상태가 정상 복구됩니다." >&2' ERR

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODE="${1:-apply}"
MYSQL_C="${MYSQL_CONTAINER:-sports-dev-mysql-1}"
ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"
GRANTS_SQL="${DIR}/../infra/mysql/grants/02-grants.sql"
ALLOW_PROD="${ALLOW_PROD:-0}"

# ── prod 오적용 가드 (리뷰 p2④) ──
# MYSQL_CONTAINER 값만 바꾸면 그대로 prod에 적용돼 flyway_migrator(ALL PRIVILEGES, 공개된 비밀번호)가
# prod에 생길 수 있다. 대상 컨테이너명에 "prod"가 포함되면 ALLOW_PROD=1 없이는 즉시 거부한다.
MYSQL_C_LOWER="$(printf '%s' "$MYSQL_C" | tr '[:upper:]' '[:lower:]')"
if [[ "$MYSQL_C_LOWER" == *prod* && "$ALLOW_PROD" != "1" ]]; then
  echo "✗ 대상 컨테이너(${MYSQL_C})가 prod로 보입니다. prod에 적용하려면 ALLOW_PROD=1 을 명시하세요." >&2
  exit 1
fi

if [[ ! -f "$GRANTS_SQL" ]]; then
  echo "✗ GRANT SQL 파일을 찾을 수 없습니다: $GRANTS_SQL" >&2
  exit 1
fi

# ── 유저별 기대 테이블 권한 개수 (리뷰 p2②) ──
# 02-grants.sql §2-1 소유권 배분표와 1:1 대응 — 테이블 수 × 4권한(SELECT/INSERT/UPDATE/DELETE).
#   svc_commerce: 11 도메인 + BATCH_* 9 = 20 × 4 = 80
#   svc_payment: 1 × 4 = 4
#   svc_facility_booking: 4 × 4 = 16
#   svc_social: 11 × 4 = 44
#   svc_platform: 18 × 4 = 72
#   svc_edge: 0 (Redis 전용, 테이블 권한 없음)
EXPECTED_USERS=(svc_commerce svc_payment svc_facility_booking svc_social svc_platform svc_edge)
EXPECTED_COUNTS=(80 4 16 44 72 0)

run_sql() {
  docker exec -i "$MYSQL_C" mysql --default-character-set=utf8mb4 -uroot -p"${ROOT_PASSWORD}" -N -B -e "$1"
}

verify_grants() {
  local failed=0
  local index
  for index in "${!EXPECTED_USERS[@]}"; do
    local grant_username="${EXPECTED_USERS[$index]}"
    local expected_count="${EXPECTED_COUNTS[$index]}"
    local actual_count
    actual_count="$(run_sql "SELECT COUNT(*) FROM information_schema.table_privileges WHERE grantee = \"'${grant_username}'@'%'\";")"
    if [[ "$actual_count" == "$expected_count" ]]; then
      echo "  ✔ ${grant_username}: ${actual_count}건 (기대값 ${expected_count})"
    else
      echo "  ✗ ${grant_username}: ${actual_count}건 (기대값 ${expected_count}) — 불일치" >&2
      failed=1
    fi
  done
  return "$failed"
}

case "$MODE" in
  apply)
    echo "▶ 서비스별 DB 유저 + GRANT 적용 (${MYSQL_C})…"
    docker exec -i "$MYSQL_C" mysql --default-character-set=utf8mb4 -uroot -p"${ROOT_PASSWORD}" \
      < "$GRANTS_SQL"
    echo "✔ GRANT 적용 완료 (멱등 — 재실행 안전)"

    echo "▶ 권한 개수 검증…"
    if ! verify_grants; then
      echo "✗ 적용 직후 권한 개수가 기대값과 다릅니다 — 02-grants.sql과 이 스크립트의 EXPECTED_COUNTS가 drift됐을 수 있습니다." >&2
      exit 1
    fi
    echo "✔ 검증 통과 — 6개 유저 권한 개수가 기대값과 일치합니다"
    echo "  롤백: DROP USER IF EXISTS 'svc_commerce'@'%', 'svc_payment'@'%', 'svc_facility_booking'@'%', 'svc_social'@'%', 'svc_platform'@'%', 'svc_edge'@'%', 'flyway_migrator'@'%';"
    ;;
  verify)
    echo "▶ 권한 개수 검증만 수행합니다 (적용 안 함, ${MYSQL_C})…"
    if ! verify_grants; then
      exit 1
    fi
    echo "✔ 검증 통과 — 6개 유저 권한 개수가 기대값과 일치합니다"
    ;;
  *)
    echo "사용법: $0 [apply|verify]" >&2
    exit 1
    ;;
esac
