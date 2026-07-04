#!/usr/bin/env bash
# qa/load/reseed/reseed.sh
#
# INFRA-08: reseed 배치 — 10분 주기로 synthetic 소진성 자원(슬롯 가용·재고·좌석)을 멱등 복원한다.
# 근거 티켓: 프로젝트/스포츠앱/상시 트래픽 시뮬레이터/Tickets/INFRA-08-reseed-배치-멱등복원.md
# 근거 TDD: 프로젝트/스포츠앱/상시 트래픽 시뮬레이터/TDD.md "실패 경로·동시성·멱등"(ADR-002)
#
# INFRA-03(qa/load/provision/provision.sh)과 역할이 다르다:
#   - INFRA-03(provision)  = synthetic 행을 "만든다"(최초 1회, 멱등 upsert). owner·범위 계약의 기준.
#   - INFRA-08(본 스크립트) = synthetic 행 중 "소비된 필드만" baseline으로 되돌린다. 행 생성/삭제는 하지 않는다
#                            (심야 전체 정리 예외 — 아래 "일 1회 전체 정리" 참고).
#
# 수행 항목 (TDD "synthetic 격리 계약"·본 티켓 정찰 확정 전략):
#   1. slot : synthetic PENDING/CONFIRMED booking 취소 → 슬롯 가용(capacity - 활성예약수) 원복
#             (reseed.sql Step 1)
#   2. stock: synthetic stocks.quantity를 baseline 절대값으로 복원(멱등 — 가산이 아니라 절대값)
#             (reseed.sql Step 2)
#   3. seat : Redis `seat:lock:{eventId}:*` 삭제(락 즉시 해제) + synthetic ISSUED 티켓 revoke
#             (reseed.sql Step 3 — active_seat_id 유니크 해제)
#   4. limited-drop: Redis `goods:limited-drop:{dropId}:*` 리셋(remaining을 초기 수량으로 재시드)
#
# 10분 주기: RESEED_RUN_ONCE=false(기본)면 RESEED_INTERVAL_SECONDS(기본 600) 간격으로 무한 루프.
#            RESEED_RUN_ONCE=true면 1회 실행 후 종료(수동 검증·cron 트리거용).
#
# 청크·jitter: MySQL 측 tickets 복원은 reseed.sql 자체가 5개 청크 + SLEEP(1~5초 랜덤)로 분산한다
#              (근거는 reseed.sql 헤더 주석). Redis 키 삭제(SCAN 기반, 소량)는 본 스크립트가
#              배치 간 소규모 sleep으로 순간 명령 폭주를 피한다.
#
# 실패 처리: 각 단계(MySQL/Redis)는 독립적으로 실패해도 스크립트를 죽이지 않는다(set -e 미사용,
#            각 단계 실패는 WARN 로그 후 다음 단계로 계속) — 절대값 복원이라 다음 10분 주기가
#            자동으로 재시도·수렴한다(TDD "reseed 부분 실패" 항목).
#
# 환경 변수 (전부 기본값 있음, MySQL 접속 계열은 provision.sh와 동일 관례):
#   MYSQL_HOST/PORT/USER/PASSWORD/ROOT_PASSWORD/DATABASE/SERVICE_NAME/COMPOSE_PROJECT_NAME
#       — provision.sh와 동일 의미·기본값(docker exec 폴백 포함).
#   REDIS_HOST              설정 시 redis-cli로 직접 접속(dev 등 host 포트 노출 환경)
#   REDIS_PORT              REDIS_HOST 사용 시 포트. 기본값 6379
#   REDIS_SERVICE_NAME      docker compose 서비스명. 기본값 redis
#   SYN_EVENT_ID            synthetic 전용 이벤트 id. 기본값 9000001(INFRA-03 baseline과 동일)
#   RESEED_INTERVAL_SECONDS 주기(초). 기본값 600(10분)
#   RESEED_RUN_ONCE         true면 1회 실행 후 종료. 기본값 false(무한 루프)
#   RESEED_FULL_CLEANUP_HOUR 일 1회 전체 정리를 수행할 UTC 시(0~23). 기본값 3(곡선 배율표 심야 구간,
#                           TDD "곡선 배율표" 00~05시=0.05배 — 트래픽 최저 구간에 무거운 정리를 몰아둔다)
#   RESEED_CLEANUP_AGE_DAYS 전체 정리 대상 최소 경과일. 기본값 1(최근 1일 이내 취소·회수 행은 보존)
#
# 안전장치:
#   - set -u로 미정의 변수 참조 즉시 실패, 파이프 실패도 감지(set -o pipefail).
#   - 모든 SQL/Redis 명령은 synthetic PK 범위(slot_id/seat_id/product_id) *그리고* synthetic
#     소유자·사용자 범위(user_id/owner_id) 이중 스코프로 제한한다(reseed.sql 헤더 주석 참고) —
#     비synthetic 데이터는 두 조건 중 하나에서 반드시 걸러진다.
#   - MySQL 접속 실패 시 baseline SQL 미적용을 WARN만 하고 종료하지 않는다(다음 주기 재시도).

set -u
set -o pipefail

# ---- 경로 계산 ----
SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIRECTORY}/../../.." && pwd)"
RESEED_SQL_FILE="${SCRIPT_DIRECTORY}/reseed.sql"
ENVIRONMENT_FILE="${REPOSITORY_ROOT}/qa/load/.env.sim"

# ---- 환경 변수 기본값 ----
MYSQL_HOST="${MYSQL_HOST:-}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${MYSQL_ROOT_PASSWORD}}"
MYSQL_DATABASE="${MYSQL_DATABASE:-sports}"
MYSQL_SERVICE_NAME="${MYSQL_SERVICE_NAME:-mysql}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-}"

REDIS_HOST="${REDIS_HOST:-}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_SERVICE_NAME="${REDIS_SERVICE_NAME:-redis}"

SYN_EVENT_ID="${SYN_EVENT_ID:-9000001}"
SYN_USER_ID_RANGE_START="${SYN_USER_ID_RANGE_START:-900000}"
SYN_USER_ID_RANGE_END="${SYN_USER_ID_RANGE_END:-999999}"

RESEED_INTERVAL_SECONDS="${RESEED_INTERVAL_SECONDS:-600}"
RESEED_RUN_ONCE="${RESEED_RUN_ONCE:-false}"
RESEED_FULL_CLEANUP_HOUR="${RESEED_FULL_CLEANUP_HOUR:-3}"
RESEED_CLEANUP_AGE_DAYS="${RESEED_CLEANUP_AGE_DAYS:-1}"

# 프로세스 생애 동안(무한 루프 1회 기동)만 유효한 "오늘 이미 전체 정리를 했는가" 마커.
# 파일 상태를 두지 않는 이유: reseed는 단일 장수 프로세스(컨테이너)로 구동되므로 메모리 변수로 충분하고,
# 컨테이너가 재기동되면 최악의 경우 같은 날 전체 정리가 한 번 더 도는 정도(멱등이라 안전)다.
# RESEED_RUN_ONCE=true(외부 cron이 매 주기 새 프로세스로 기동하는 방식)로 운용하면 이 메모리 마커가
# 매번 초기화되므로, 정리 대상 시(hour)에 cron이 여러 번 걸치면 그 시간대 동안 정리 SQL이 여러 번
# 실행될 수 있다 — 이미 지워진 행은 DELETE의 WHERE에 다시 걸리지 않으므로 매번 0행 영향으로 수렴해
# 안전하다(추가 부작용 없음, 약간의 낭비 호출만 발생).
LAST_FULL_CLEANUP_DATE=""

log() {
    echo "[reseed] $*"
}

warn() {
    echo "[reseed][WARN] $*" >&2
}

# ---- 안전장치: MYSQL_HOST/REDIS_HOST 오조준 차단 ----
# provision.sh#assert_safe_target·qa/load/k6/lib/auth.js#assertSafeTarget과 동일한 안전 규칙을
# MySQL/Redis 직접 접속 대상에도 적용한다. 실행 셸의 프로파일(.zshrc 등)에 다른 프로젝트용
# MYSQL_HOST(예: 실제 RDS 클러스터 주소)가 전역으로 설정돼 있을 수 있음을 실제로 확인했다 —
# reseed는 사람이 매번 확인하지 않는 무인 반복 배치이므로, 로컬이 아닌 호스트가 감지되면
# 그 단계만 조용히 스킵하는 게 아니라 즉시 중단해 원격 DB/Redis에 대한 오조준 UPDATE/DELETE를 막는다.
assert_safe_mysql_target() {
    if [ -z "${MYSQL_HOST}" ]; then
        return 0
    fi
    # 허용: localhost·127.0.0.1·*.local + docker compose 서비스명(mysql/redis 등 점·슬래시 없는 단일 호스트).
    # 차단: RDS·FQDN·URL 등 점(.)이나 슬래시(/)를 포함하는 원격 오조준 대상.
    if [[ "${MYSQL_HOST}" =~ ^(127\.0\.0\.1|[^./]+|[^/]*\.local)$ ]]; then
        return 0
    fi
    warn "[SAFETY] MYSQL_HOST=${MYSQL_HOST}는 로컬/컨테이너 대상이 아닙니다. reseed는 무인 반복 배치이므로" \
         "점(.)·슬래시(/) 포함 원격 호스트에는 절대 실행하지 않습니다(오조준 방지)."
    exit 1
}

assert_safe_redis_target() {
    if [ -z "${REDIS_HOST}" ]; then
        return 0
    fi
    # 허용: localhost·127.0.0.1·*.local + docker compose 서비스명(mysql/redis 등 점·슬래시 없는 단일 호스트).
    if [[ "${REDIS_HOST}" =~ ^(127\.0\.0\.1|[^./]+|[^/]*\.local)$ ]]; then
        return 0
    fi
    warn "[SAFETY] REDIS_HOST=${REDIS_HOST}는 로컬/컨테이너 대상이 아닙니다. reseed는 무인 반복 배치이므로" \
         "점(.)·슬래시(/) 포함 원격 호스트에는 절대 실행하지 않습니다(오조준 방지)."
    exit 1
}

# ---- .env.sim 읽기 헬퍼 (provision.sh와 동일 관례 — 값이 없으면 빈 문자열) ----
load_environment_variable() {
    local variable_name="$1"
    if [ -f "${ENVIRONMENT_FILE}" ]; then
        grep -E "^${variable_name}=" "${ENVIRONMENT_FILE}" 2>/dev/null | tail -n 1 | cut -d '=' -f 2- || true
    fi
}

# ============================================================
# MySQL 실행 헬퍼 — provision.sh#apply_baseline_sql과 동일한 3단 폴백
#   (MYSQL_HOST 직접 접속 → docker compose exec → 라벨 기반 docker exec)
# 인자: $1 = 실행할 SQL 문자열 또는 파일 경로 표시용 설명, $2 = SQL 파일 경로
# ============================================================
run_mysql_file() {
    local description="$1"
    local sql_file="$2"

    if [ -n "${MYSQL_HOST}" ]; then
        if ! command -v mysql >/dev/null 2>&1; then
            warn "${description}: mysql 클라이언트가 설치돼 있지 않습니다 — 스킵"
            return 1
        fi
        if mysql -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${MYSQL_DATABASE}" < "${sql_file}"; then
            log "${description}: 적용 완료 (mysql client, host=${MYSQL_HOST})"
            return 0
        fi
        warn "${description}: mysql 클라이언트 적용 실패"
        return 1
    fi

    local compose_project_flag=()
    if [ -n "${COMPOSE_PROJECT_NAME}" ]; then
        compose_project_flag=(-p "${COMPOSE_PROJECT_NAME}")
    fi

    if (cd "${REPOSITORY_ROOT}" && docker compose "${compose_project_flag[@]+"${compose_project_flag[@]}"}" exec -T "${MYSQL_SERVICE_NAME}" \
            mysql -u"${MYSQL_USER}" -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < "${sql_file}") 2>/dev/null; then
        log "${description}: 적용 완료 (docker compose exec, service=${MYSQL_SERVICE_NAME})"
        return 0
    fi

    warn "${description}: docker compose exec 실패 — 라벨 기반 컨테이너 탐색 폴백"
    local compose_label_filters=(--filter "label=com.docker.compose.service=${MYSQL_SERVICE_NAME}")
    if [ -n "${COMPOSE_PROJECT_NAME}" ]; then
        compose_label_filters+=(--filter "label=com.docker.compose.project=${COMPOSE_PROJECT_NAME}")
    fi
    local mysql_container_name
    mysql_container_name="$(docker ps "${compose_label_filters[@]}" --format '{{.Names}}' 2>/dev/null | head -n 1 || true)"
    if [ -z "${mysql_container_name}" ]; then
        mysql_container_name="$(docker ps --format '{{.Names}}' 2>/dev/null | grep -i "${MYSQL_SERVICE_NAME}" | head -n 1 || true)"
    fi
    if [ -z "${mysql_container_name}" ]; then
        warn "${description}: 실행 중인 mysql 컨테이너를 찾지 못했습니다 — 스킵"
        return 1
    fi

    if docker exec -i "${mysql_container_name}" mysql -u"${MYSQL_USER}" -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < "${sql_file}"; then
        log "${description}: 적용 완료 (docker exec, container=${mysql_container_name})"
        return 0
    fi

    warn "${description}: docker exec 적용도 실패했습니다 — 스킵(다음 주기 재시도)"
    return 1
}

# ============================================================
# Redis 실행 헬퍼 — SCAN 기반(KEYS 금지, private-redis-convention) 패턴 매칭 삭제
# 인자: $1 = 삭제할 key 패턴 (예: "seat:lock:9000001:*")
# ============================================================
redis_exec() {
    # $@ 를 그대로 redis-cli 인자로 전달
    if [ -n "${REDIS_HOST}" ]; then
        if ! command -v redis-cli >/dev/null 2>&1; then
            warn "redis-cli가 설치돼 있지 않습니다 — Redis 단계 스킵"
            return 1
        fi
        redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" "$@"
        return $?
    fi

    local compose_project_flag=()
    if [ -n "${COMPOSE_PROJECT_NAME}" ]; then
        compose_project_flag=(-p "${COMPOSE_PROJECT_NAME}")
    fi
    if (cd "${REPOSITORY_ROOT}" && docker compose "${compose_project_flag[@]+"${compose_project_flag[@]}"}" exec -T "${REDIS_SERVICE_NAME}" \
            redis-cli "$@") 2>/dev/null; then
        return 0
    fi

    local redis_container_name
    redis_container_name="$(docker ps --filter "label=com.docker.compose.service=${REDIS_SERVICE_NAME}" --format '{{.Names}}' 2>/dev/null | head -n 1 || true)"
    if [ -z "${redis_container_name}" ]; then
        redis_container_name="$(docker ps --format '{{.Names}}' 2>/dev/null | grep -i "${REDIS_SERVICE_NAME}" | head -n 1 || true)"
    fi
    if [ -z "${redis_container_name}" ]; then
        warn "실행 중인 redis 컨테이너를 찾지 못했습니다 — Redis 단계 스킵"
        return 1
    fi
    docker exec -i "${redis_container_name}" redis-cli "$@"
}

# synthetic 키 패턴을 SCAN으로 찾아 삭제한다(KEYS 명령 사용 금지 — private-redis-convention).
# 매칭 키가 없으면 아무 것도 하지 않는다(멱등 — 이미 지워졌거나 애초에 없던 키는 no-op).
delete_keys_matching() {
    local pattern="$1"
    local cursor="0"
    local deleted_count=0
    while :; do
        local scan_result
        # redis-cli는 비-tty(파이프) 실행 시 기본이 raw 모드라 "커서\n키1\n키2..." 형태의
        # 평평한 줄 단위 출력을 낸다(--no-raw를 주면 대신 사람이 읽는 중첩 "1) ...) ..." 포맷이 되어
        # 아래 줄 단위 파싱이 깨지므로 반드시 raw 그대로 받는다).
        scan_result="$(redis_exec SCAN "${cursor}" MATCH "${pattern}" COUNT 200 2>/dev/null || true)"
        if [ -z "${scan_result}" ]; then
            break
        fi
        cursor="$(echo "${scan_result}" | sed -n '1p' | tr -d '"')"
        local matched_keys
        matched_keys="$(echo "${scan_result}" | tail -n +2 | tr -d '"')"
        if [ -n "${matched_keys}" ]; then
            while IFS= read -r matched_key; do
                [ -z "${matched_key}" ] && continue
                redis_exec DEL "${matched_key}" >/dev/null 2>&1 || true
                deleted_count=$((deleted_count + 1))
            done <<< "${matched_keys}"
        fi
        if [ "${cursor}" = "0" ]; then
            break
        fi
    done
    log "Redis 패턴 '${pattern}' 삭제된 키: ${deleted_count}개"
}

# ============================================================
# Step A: MySQL 절대 복원 (bookings 취소 + stocks 복원 + tickets revoke, 청크·jitter는 reseed.sql 내부)
# ============================================================
run_mysql_reseed() {
    if [ ! -f "${RESEED_SQL_FILE}" ]; then
        warn "reseed.sql을 찾을 수 없습니다: ${RESEED_SQL_FILE} — MySQL 복원 스킵"
        return 1
    fi
    run_mysql_file "MySQL 복원(slot/stock/seat)" "${RESEED_SQL_FILE}"
}

# ============================================================
# Step B: 좌석 락 Redis 삭제 — synthetic 이벤트 소유 좌석 락만 대상
# ============================================================
run_redis_seat_lock_reset() {
    log "Redis 좌석 락 삭제 시작: seat:lock:${SYN_EVENT_ID}:*"
    delete_keys_matching "seat:lock:${SYN_EVENT_ID}:*"
}

# ============================================================
# Step C: 한정판 drop 게이트 Redis 리셋 — .env.sim의 DROP_ID가 있을 때만 수행
# ============================================================
run_redis_limited_drop_reset() {
    local drop_id
    drop_id="$(load_environment_variable DROP_ID)"
    if [ -z "${drop_id}" ]; then
        log "DROP_ID가 .env.sim에 없습니다 — 한정판 drop Redis 리셋 스킵(③ 미배포 또는 provision 미실행)"
        return 0
    fi

    local drop_limited_quantity
    drop_limited_quantity="$(load_environment_variable DROP_LIMITED_QUANTITY)"
    drop_limited_quantity="${drop_limited_quantity:-100000000}"

    log "Redis 한정판 drop(dropId=${drop_id}) 리셋 시작: goods:limited-drop:${drop_id}:*"
    delete_keys_matching "goods:limited-drop:${drop_id}:*"

    # remaining 키를 초기 수량으로 재시드(절대값 SET — DropReservationStoreImpl#seedIfAbsent와
    # 동일 키 규약이지만, reseed는 방금 위에서 DEL했으므로 SETNX가 아니라 SET으로 즉시 채운다).
    redis_exec SET "goods:limited-drop:${drop_id}:remaining" "${drop_limited_quantity}" >/dev/null 2>&1 || true
    log "Redis 한정판 drop(dropId=${drop_id}) remaining=${drop_limited_quantity}로 재시드 완료"
}

# ============================================================
# Step D (조건부, 일 1회): 심야(배율 최저 구간) synthetic 누적분 전체 정리
#   TDD Open Questions "synthetic 데이터 정리 주기" 해소 — 오래된(경과일 이상) 이미 종결된
#   synthetic 행(취소된 예약·회수된 티켓과 그 주문)을 hard delete해 무한 성장을 막는다.
#   재실행해도 이미 지워진 행은 WHERE에 걸리지 않아 멱등하다.
# ============================================================
run_daily_full_cleanup_if_due() {
    local current_hour current_date
    current_hour="$(date -u +%H | sed 's/^0*//')"
    current_hour="${current_hour:-0}"
    current_date="$(date -u +%Y-%m-%d)"

    if [ "${current_hour}" != "${RESEED_FULL_CLEANUP_HOUR}" ]; then
        return 0
    fi
    if [ "${current_date}" = "${LAST_FULL_CLEANUP_DATE}" ]; then
        log "오늘(${current_date}) 전체 정리를 이미 수행했습니다 — 스킵"
        return 0
    fi

    log "=== 일 1회 synthetic 전체 정리 시작 (UTC ${current_hour}시, age>=${RESEED_CLEANUP_AGE_DAYS}일) ==="

    local cleanup_sql_file
    cleanup_sql_file="$(mktemp)"
    cat > "${cleanup_sql_file}" <<SQL
-- synthetic 범위 + 경과일 이중 조건 — 최근 활동은 보존, 오래 종결된 행만 정리
DELETE t FROM tickets t
    JOIN ticket_orders o ON o.id = t.ticket_order_id
WHERE t.seat_id BETWEEN 9000001 AND 9005000
  AND o.user_id BETWEEN ${SYN_USER_ID_RANGE_START} AND ${SYN_USER_ID_RANGE_END}
  AND t.status = 'REVOKED'
  AND t.deleted_at IS NOT NULL
  AND t.updated_at < DATE_SUB(NOW(6), INTERVAL ${RESEED_CLEANUP_AGE_DAYS} DAY);

DELETE FROM ticket_orders
WHERE locked_event_id = ${SYN_EVENT_ID}
  AND user_id BETWEEN ${SYN_USER_ID_RANGE_START} AND ${SYN_USER_ID_RANGE_END}
  AND status = 'CANCELLED'
  AND updated_at < DATE_SUB(NOW(6), INTERVAL ${RESEED_CLEANUP_AGE_DAYS} DAY);

DELETE FROM bookings
WHERE slot_id BETWEEN 9000001 AND 9000030
  AND user_id BETWEEN ${SYN_USER_ID_RANGE_START} AND ${SYN_USER_ID_RANGE_END}
  AND status = 'CANCELLED'
  AND updated_at < DATE_SUB(NOW(6), INTERVAL ${RESEED_CLEANUP_AGE_DAYS} DAY);

-- B2B 등록 곡선(INFRA-06)이 매 iteration INSERT하는 synthetic 상품/이벤트(name/title prefix 'b2b-load-')
-- 를 경과일 조건으로 정리한다. owner는 partner 연동 User라 synthetic owner 스코프에 안 걸리므로
-- prefix로 식별한다. 무한 성장 방지 + 조회 지연 측정(INFRA-04) 왜곡 방지. 자식(stocks/seats) 먼저 삭제.
DELETE s FROM stocks s
    JOIN products p ON p.id = s.product_id
WHERE p.name LIKE 'b2b-load-product-%'
  AND p.created_at < DATE_SUB(NOW(6), INTERVAL ${RESEED_CLEANUP_AGE_DAYS} DAY);

DELETE FROM products
WHERE name LIKE 'b2b-load-product-%'
  AND created_at < DATE_SUB(NOW(6), INTERVAL ${RESEED_CLEANUP_AGE_DAYS} DAY);

DELETE se FROM seats se
    JOIN events e ON e.id = se.event_id
WHERE e.title LIKE 'b2b-load-event-%'
  AND e.created_at < DATE_SUB(NOW(6), INTERVAL ${RESEED_CLEANUP_AGE_DAYS} DAY);

DELETE FROM events
WHERE title LIKE 'b2b-load-event-%'
  AND created_at < DATE_SUB(NOW(6), INTERVAL ${RESEED_CLEANUP_AGE_DAYS} DAY);
SQL

    if run_mysql_file "일 1회 전체 정리" "${cleanup_sql_file}"; then
        LAST_FULL_CLEANUP_DATE="${current_date}"
        log "=== 일 1회 synthetic 전체 정리 완료 ==="
    else
        warn "일 1회 전체 정리 실패 — 다음 주기(같은 날 내 재시도 또는 내일)로 넘어감"
    fi
    rm -f "${cleanup_sql_file}"
}

# ============================================================
# 1회 reseed 사이클
# ============================================================
run_one_cycle() {
    log "=== reseed 사이클 시작 $(date -u +%Y-%m-%dT%H:%M:%SZ) ==="

    if run_mysql_reseed; then
        log "Step A 완료: MySQL 절대 복원(slot/stock/seat)"
    else
        warn "Step A 스킵됨 — 다음 주기 재시도"
    fi

    if run_redis_seat_lock_reset; then
        log "Step B 완료: 좌석 락 Redis 삭제"
    else
        warn "Step B 스킵됨 — 좌석 락은 300s TTL 자연 만료로도 보완됨(TDD 실패 경로)"
    fi

    run_redis_limited_drop_reset

    run_daily_full_cleanup_if_due

    log "=== reseed 사이클 종료 $(date -u +%Y-%m-%dT%H:%M:%SZ) ==="
}

main() {
    assert_safe_mysql_target
    assert_safe_redis_target
    log "=== INFRA-08 reseed 배치 시작 (interval=${RESEED_INTERVAL_SECONDS}s, run_once=${RESEED_RUN_ONCE}) ==="

    if [ "${RESEED_RUN_ONCE}" = "true" ]; then
        run_one_cycle
        log "RESEED_RUN_ONCE=true — 1회 실행 후 종료"
        exit 0
    fi

    while :; do
        run_one_cycle
        sleep "${RESEED_INTERVAL_SECONDS}"
    done
}

main "$@"
