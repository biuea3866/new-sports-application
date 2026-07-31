#!/usr/bin/env bash
# [W1-04] 서비스별 DB 유저 + 테이블 단위 GRANT — 적용/검증 스크립트.
#
# 두 가지 호출 경로가 이 스크립트 하나를 공유한다(재리뷰 p2② 후속 — verify 로직 중복 제거):
#   1. docker-compose.dev.yml 의 `db-grants` 원샷 사이드카 — 컨테이너 안에서 MYSQL_HOST=mysql 로
#      컴포즈 내부 네트워크에 직접 접속한다(이 스크립트를 볼륨 마운트해 그대로 실행).
#   2. 사이드카를 쓰지 않는 수동 재적용 — 호스트에서 MYSQL_CONTAINER 를 지정해 docker exec 로 접속한다.
# 연결 방식은 MYSQL_HOST 존재 여부로 자동 분기된다(아래 run_sql 참고). apply/verify 로직 자체는
# 두 경로가 완전히 동일한 코드를 실행한다.
#
# db-grants 사이드카는 dev 스택에만 정의돼 있다(docker-compose.yml 이 아니라 docker-compose.dev.yml
# — 재리뷰 p0. base 에 두면 prod override 병합에도 상속돼 이 파일의 평문 비밀번호 유저가 prod에
# 무조건 생성되는 사고가 났다). prod 반영은 별도 시크릿·가드 설계가 선행돼야 한다(2단계, 범위 밖).
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
#   scripts/apply-grants.sh          # 적용 + 적용 직후 권한 개수 검증(검증 실패 시 exit 1)
#   scripts/apply-grants.sh verify   # 적용 없이 현재 권한 개수만 검증(드리프트 확인용)
#
# 환경변수:
#   MYSQL_HOST          - 설정하면 docker exec 대신 이 호스트로 mysql 클라이언트가 직접 접속한다
#                          (db-grants 사이드카 전용 경로 — 컴포즈 내부 네트워크, 컨테이너 이름/
#                          docker socket 의존 없음). 미설정 시 아래 MYSQL_CONTAINER 로 docker exec.
#   MYSQL_CONTAINER     - 대상 mysql 컨테이너명 (기본: sports-dev-mysql-1). MYSQL_HOST 미설정일 때만 사용.
#   MYSQL_ROOT_PASSWORD - root 비밀번호 (기본: root, docker-compose.yml 기본값과 동일)
#   GRANTS_SQL          - 02-grants.sql 경로 override (기본: 이 스크립트 기준 상대 경로 탐색).
#                          사이드카처럼 단독 마운트돼 상대 경로가 성립하지 않는 실행 환경에서 사용.
#   ALLOW_PROD          - MYSQL_HOST 미설정(docker exec 모드)일 때만 적용되는 안전장치. 대상
#                          컨테이너명에 "prod"가 포함될 때만 확인한다. 1이 아니면 prod로 보이는
#                          대상에 대해 즉시 거부한다(아래 "prod 호출" 참고). MYSQL_HOST 모드는
#                          이 가드를 적용하지 않는다 — 대신 db-grants 사이드카가 dev 스택에만
#                          정의돼 있다는 구조 자체가 prod 오적용을 막는다(재리뷰 p0).
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
MYSQL_HOST="${MYSQL_HOST:-}"
MYSQL_C="${MYSQL_CONTAINER:-sports-dev-mysql-1}"
ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"
GRANTS_SQL="${GRANTS_SQL:-${DIR}/../infra/mysql/grants/02-grants.sql}"
ALLOW_PROD="${ALLOW_PROD:-0}"

# ── prod 오적용 가드 (리뷰 p2④) — docker exec 모드에서만 의미가 있다 ──
# MYSQL_CONTAINER 값만 바꾸면 그대로 prod에 적용돼 flyway_migrator(ALL PRIVILEGES, 공개된 비밀번호)가
# prod에 생길 수 있다. 대상 컨테이너명에 "prod"가 포함되면 ALLOW_PROD=1 없이는 즉시 거부한다.
# MYSQL_HOST 모드(db-grants 사이드카)는 이 가드 대신 "dev 스택에만 정의됨"이라는 구조로 방어한다.
if [[ -z "$MYSQL_HOST" ]]; then
  MYSQL_C_LOWER="$(printf '%s' "$MYSQL_C" | tr '[:upper:]' '[:lower:]')"
  if [[ "$MYSQL_C_LOWER" == *prod* && "$ALLOW_PROD" != "1" ]]; then
    echo "✗ 대상 컨테이너(${MYSQL_C})가 prod로 보입니다. prod에 적용하려면 ALLOW_PROD=1 을 명시하세요." >&2
    exit 1
  fi
fi

if [[ ! -f "$GRANTS_SQL" ]]; then
  echo "✗ GRANT SQL 파일을 찾을 수 없습니다: $GRANTS_SQL" >&2
  exit 1
fi

# ── 유저별 기대 테이블 권한 개수 — 02-grants.sql 을 단일 소스로 파생한다 (리뷰 p3 후속) ──
# 이전에는 apply-grants.sh(이 파일)·DbGrantPermissionTest.kt 두 곳에 동일 숫자(80/4/16/44/72/0)가
# 하드코딩돼 있어 02-grants.sql이 바뀌어도 두 곳을 손으로 갱신하지 않는 한 조용히 drift됐다.
# 이제는 GRANTS_SQL 실제 내용에서 "TO '<user>'@'%';" 로 끝나는 GRANT 라인 수를 세어, 그 라인마다
# 4권한(SELECT/INSERT/UPDATE/DELETE)이 부여되므로 ×4 한 값을 기대값으로 쓴다.
SERVICE_USERS=(svc_commerce svc_payment svc_facility_booking svc_social svc_platform svc_edge)

expected_count_for_user() {
  local grant_username="$1"
  local grant_line_count
  grant_line_count="$(grep -c "TO '${grant_username}'@'%';" "$GRANTS_SQL" || true)"
  echo $(( grant_line_count * 4 ))
}

# 연결 방식 분기: MYSQL_HOST 가 설정돼 있으면 컴포즈 내부 네트워크로 직접 접속(db-grants 사이드카),
# 아니면 기존처럼 docker exec 로 대상 컨테이너에 접속한다.
run_sql() {
  if [[ -n "$MYSQL_HOST" ]]; then
    mysql -h "$MYSQL_HOST" --default-character-set=utf8mb4 -uroot -p"${ROOT_PASSWORD}" -N -B -e "$1"
  else
    docker exec -i "$MYSQL_C" mysql --default-character-set=utf8mb4 -uroot -p"${ROOT_PASSWORD}" -N -B -e "$1"
  fi
}

import_grants_sql() {
  if [[ -n "$MYSQL_HOST" ]]; then
    mysql -h "$MYSQL_HOST" --default-character-set=utf8mb4 -uroot -p"${ROOT_PASSWORD}" < "$GRANTS_SQL"
  else
    docker exec -i "$MYSQL_C" mysql --default-character-set=utf8mb4 -uroot -p"${ROOT_PASSWORD}" < "$GRANTS_SQL"
  fi
}

# 접속 실패(run_sql 자체 오류)와 권한 드리프트(개수 불일치)를 구분한다 (리뷰 p3 후속) — 이전에는
# `if ! verify_grants` 조건 안에서 errexit이 비활성화돼, run_sql이 실패해도 actual_count가 빈
# 문자열이 된 채 "건 (기대값 N) — 불일치"로 보고돼 두 실패 원인이 뒤섞였다.
verify_grants() {
  local failed=0
  local grant_username
  for grant_username in "${SERVICE_USERS[@]}"; do
    local expected_count
    expected_count="$(expected_count_for_user "$grant_username")"
    local actual_count
    if ! actual_count="$(run_sql "SELECT COUNT(*) FROM information_schema.table_privileges WHERE grantee = \"'${grant_username}'@'%'\";")"; then
      echo "  ✗ ${grant_username}: 접속 실패 — run_sql 오류(컨테이너/네트워크 상태를 확인하세요)" >&2
      failed=1
      continue
    fi
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
    echo "▶ 서비스별 DB 유저 + GRANT 적용 (${MYSQL_HOST:-$MYSQL_C})…"
    import_grants_sql
    echo "✔ GRANT 적용 완료 (멱등 — 재실행 안전)"

    echo "▶ 권한 개수 검증…"
    if ! verify_grants; then
      echo "✗ 적용 직후 권한 개수가 기대값과 다릅니다 — GRANT가 일부만 적용됐거나 접속에 실패했습니다." >&2
      exit 1
    fi
    echo "✔ 검증 통과 — ${#SERVICE_USERS[@]}개 유저 권한 개수가 기대값과 일치합니다"
    echo "  롤백: DROP USER IF EXISTS 'svc_commerce'@'%', 'svc_payment'@'%', 'svc_facility_booking'@'%', 'svc_social'@'%', 'svc_platform'@'%', 'svc_edge'@'%', 'flyway_migrator'@'%';"
    ;;
  verify)
    echo "▶ 권한 개수 검증만 수행합니다 (적용 안 함, ${MYSQL_HOST:-$MYSQL_C})…"
    if ! verify_grants; then
      exit 1
    fi
    echo "✔ 검증 통과 — ${#SERVICE_USERS[@]}개 유저 권한 개수가 기대값과 일치합니다"
    ;;
  *)
    echo "사용법: $0 [apply|verify]" >&2
    exit 1
    ;;
esac
