#!/usr/bin/env bash
# qa/load/provision/provision.sh
#
# INFRA-03: synthetic provision — 유저 풀 범위 확정 · B2B 파트너 키 발급 · 마케팅 drop 사전 개설.
# 근거 티켓: 프로젝트/스포츠앱/상시 트래픽 시뮬레이터/Tickets/INFRA-03-synthetic-provision-시드-발급.md
# 근거 TDD: 프로젝트/스포츠앱/상시 트래픽 시뮬레이터/TDD.md "synthetic 격리 계약"
#
# 수행 순서:
#   1. qa/load/seeds/simulator-baseline.sql 적용 (synthetic 시설/슬롯·상품/재고·이벤트/좌석 baseline)
#   2. POST /api/admin/partners (② B2B 파트너 연동, ADMIN 필요) — plainApiKey를 .env.sim에 기록
#   3. POST /limited-drops (③ 마케팅 이벤트 고부하 대응) — dropId를 .env.sim에 기록
#
# 멱등성:
#   - Step 1은 매 실행마다 절대값으로 upsert(진짜 멱등, 반복 실행 권장 — reseed와 동일 로직).
#   - Step 2·3은 대상 API가 "항상 새 리소스 생성"이라 서버 측 멱등이 없다. 이미 .env.sim에
#     PARTNER_API_KEY/DROP_ID가 있으면 재실행 시 스킵한다(재발급하려면 FORCE_REPROVISION=true).
#   - ② 또는 ③ 엔드포인트가 배포되지 않았거나(404) ADMIN 인증이 구성되지 않았으면 해당 단계만
#     WARN 후 스킵하고 나머지 단계는 계속 진행한다(부분 가용, TDD "실패 경로" 표 준수).
#
# 환경 변수 (전부 기본값 있음):
#   TARGET_URL              대상 API 베이스 URL. 기본값 http://localhost:8088 (nginx-lb, docker-compose.lb.yml)
#   MYSQL_HOST              설정 시 docker exec 대신 mysql 클라이언트로 직접 접속(dev 등 host 포트 노출 환경)
#   MYSQL_PORT              MYSQL_HOST 사용 시 포트. 기본값 3306
#   MYSQL_USER              MYSQL_HOST 사용 시 사용자. 기본값 root
#   MYSQL_PASSWORD          MYSQL_HOST 사용 시 비밀번호. 기본값 MYSQL_ROOT_PASSWORD 값
#   MYSQL_ROOT_PASSWORD     docker exec 경로에서 mysql 컨테이너 root 비밀번호. 기본값 root(compose 기본값과 일치)
#   MYSQL_DATABASE          대상 스키마. 기본값 sports
#   MYSQL_SERVICE_NAME      docker compose 서비스명. 기본값 mysql
#   COMPOSE_PROJECT_NAME    docker compose -p 프로젝트명(prod는 sports-prod 등). 미설정 시 compose 기본 추론에 위임
#   ADMIN_EMAIL/ADMIN_PASSWORD   ② 파트너 발급용 ADMIN 계정 로그인 정보. 미설정 시 파트너 발급 단계 스킵
#   PARTNER_NAME            발급할 synthetic 파트너 표시명. 기본값 synthetic-load-partner
#   DROP_PRODUCT_ID         한정판 drop 대상 상품 id. 기본값 9010001(baseline 상품 풀의 첫 상품)
#   DROP_OWNER_USER_ID      drop 개설 X-User-Id(상품 owner_id와 일치해야 함). 기본값 1
#   DROP_LIMITED_QUANTITY   drop 한정 수량. 기본값 100000000(스파이크 트래픽 전량 흡수)
#   DROP_PER_USER_LIMIT     1인 구매 한도. 기본값 1000000(부하 스크립트가 한도 초과로 막히지 않게 크게)
#   FORCE_REPROVISION       true면 기존 .env.sim의 PARTNER_API_KEY/DROP_ID를 무시하고 재발급 시도
#   QA_USER_POOL_SIZE       유저 풀 크기(문서화 목적 — X-User-Id 헤더 모델이라 실제 유저 row 생성 불요)
#
# 안전장치: set -u로 미정의 변수 참조를 즉시 실패시키고, 스킵이 의도된 지점만 명시적으로 `|| true`로 감싼다.

set -u
set -o pipefail

# ---- 경로 계산 ----
SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIRECTORY}/../../.." && pwd)"
BASELINE_SEED_FILE="${REPOSITORY_ROOT}/qa/load/seeds/simulator-baseline.sql"
ENVIRONMENT_FILE="${REPOSITORY_ROOT}/qa/load/.env.sim"

# ---- 환경 변수 기본값 ----
TARGET_URL="${TARGET_URL:-http://localhost:8088}"
MYSQL_HOST="${MYSQL_HOST:-}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${MYSQL_ROOT_PASSWORD}}"
MYSQL_DATABASE="${MYSQL_DATABASE:-sports}"
MYSQL_SERVICE_NAME="${MYSQL_SERVICE_NAME:-mysql}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-}"
ADMIN_EMAIL="${ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"
PARTNER_NAME="${PARTNER_NAME:-synthetic-load-partner}"
DROP_PRODUCT_ID="${DROP_PRODUCT_ID:-9010001}"
DROP_OWNER_USER_ID="${DROP_OWNER_USER_ID:-1}"
DROP_LIMITED_QUANTITY="${DROP_LIMITED_QUANTITY:-100000000}"
DROP_PER_USER_LIMIT="${DROP_PER_USER_LIMIT:-1000000}"
FORCE_REPROVISION="${FORCE_REPROVISION:-false}"
QA_USER_POOL_SIZE="${QA_USER_POOL_SIZE:-1000}"

# ---- 로그 헬퍼 ----
log() {
    echo "[provision] $*"
}

warn() {
    echo "[provision][WARN] $*" >&2
}

# ---- .env.sim 읽기/쓰기 헬퍼 (멱등 — 동일 키 재기록 시 기존 라인을 치환) ----
load_environment_variable() {
    local variable_name="$1"
    if [ -f "${ENVIRONMENT_FILE}" ]; then
        grep -E "^${variable_name}=" "${ENVIRONMENT_FILE}" 2>/dev/null | tail -n 1 | cut -d '=' -f 2- || true
    fi
}

write_environment_variable() {
    local variable_name="$1"
    local variable_value="$2"
    mkdir -p "$(dirname "${ENVIRONMENT_FILE}")"
    touch "${ENVIRONMENT_FILE}"
    if grep -qE "^${variable_name}=" "${ENVIRONMENT_FILE}" 2>/dev/null; then
        local temporary_file
        temporary_file="$(mktemp)"
        grep -vE "^${variable_name}=" "${ENVIRONMENT_FILE}" > "${temporary_file}" || true
        mv "${temporary_file}" "${ENVIRONMENT_FILE}"
    fi
    echo "${variable_name}=${variable_value}" >> "${ENVIRONMENT_FILE}"
}

# ---- JSON 필드 추출 헬퍼 (jq 우선, 없으면 grep/sed 폴백) ----
extract_json_string_field() {
    local json_body="$1"
    local field_name="$2"
    if command -v jq >/dev/null 2>&1; then
        echo "${json_body}" | jq -r --arg field "${field_name}" '.[$field] // empty' 2>/dev/null
    else
        echo "${json_body}" | grep -o "\"${field_name}\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -n 1 | sed -E 's/.*:[[:space:]]*"([^"]*)"/\1/'
    fi
}

extract_json_number_field() {
    local json_body="$1"
    local field_name="$2"
    if command -v jq >/dev/null 2>&1; then
        echo "${json_body}" | jq -r --arg field "${field_name}" '.[$field] // empty' 2>/dev/null
    else
        echo "${json_body}" | grep -o "\"${field_name}\"[[:space:]]*:[[:space:]]*[0-9]*" | head -n 1 | sed -E 's/.*:[[:space:]]*([0-9]*)/\1/'
    fi
}

# ---- 플랫폼 독립 "먼 미래" ISO-8601 UTC 시각 (GNU date / BSD date 둘 다 지원) ----
far_future_iso_timestamp() {
    if date -v +5y >/dev/null 2>&1; then
        date -u -v +5y +"%Y-%m-%dT%H:%M:%S.000Z"
    else
        date -u -d "+5 years" +"%Y-%m-%dT%H:%M:%S.000Z"
    fi
}

current_iso_timestamp() {
    date -u +"%Y-%m-%dT%H:%M:%S.000Z"
}

# ============================================================
# Step 1: synthetic baseline SQL 적용
# ============================================================
apply_baseline_sql() {
    if [ ! -f "${BASELINE_SEED_FILE}" ]; then
        warn "시드 파일을 찾을 수 없습니다: ${BASELINE_SEED_FILE} — baseline SQL 적용 스킵"
        return 1
    fi

    if [ -n "${MYSQL_HOST}" ]; then
        log "MYSQL_HOST=${MYSQL_HOST} 설정됨 — mysql 클라이언트로 직접 적용"
        if ! command -v mysql >/dev/null 2>&1; then
            warn "mysql 클라이언트가 설치돼 있지 않습니다 — baseline SQL 적용 스킵"
            return 1
        fi
        if mysql -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${MYSQL_DATABASE}" < "${BASELINE_SEED_FILE}"; then
            log "baseline SQL 적용 완료 (mysql client, host=${MYSQL_HOST})"
            return 0
        fi
        warn "mysql 클라이언트 적용 실패 — baseline SQL 스킵"
        return 1
    fi

    log "MYSQL_HOST 미설정 — docker compose exec으로 mysql 컨테이너에 직접 적용 시도"
    local compose_project_flag=()
    if [ -n "${COMPOSE_PROJECT_NAME}" ]; then
        compose_project_flag=(-p "${COMPOSE_PROJECT_NAME}")
    fi

    if (cd "${REPOSITORY_ROOT}" && docker compose "${compose_project_flag[@]+"${compose_project_flag[@]}"}" exec -T "${MYSQL_SERVICE_NAME}" \
            mysql -u"${MYSQL_USER}" -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < "${BASELINE_SEED_FILE}") 2>/dev/null; then
        log "baseline SQL 적용 완료 (docker compose exec, service=${MYSQL_SERVICE_NAME})"
        return 0
    fi

    warn "docker compose exec 적용 실패 — docker compose 라벨 기반 컨테이너 탐색 후 docker exec 폴백 시도"
    # 공유 개발 호스트에는 다른 프로젝트의 mysql 컨테이너가 동시에 떠 있을 수 있다(예: 다른 앱의
    # "stock-mysql" 등). 컨테이너 이름에 "mysql" 문자열만으로 매칭하면 엉뚱한 프로젝트의 DB에
    # synthetic 시드를 적용하는 사고로 이어질 수 있어, docker compose가 실제로 붙이는
    # com.docker.compose.service 라벨로 먼저 좁힌다(COMPOSE_PROJECT_NAME이 있으면 프로젝트까지 좁힌다).
    local compose_label_filters=(--filter "label=com.docker.compose.service=${MYSQL_SERVICE_NAME}")
    if [ -n "${COMPOSE_PROJECT_NAME}" ]; then
        compose_label_filters+=(--filter "label=com.docker.compose.project=${COMPOSE_PROJECT_NAME}")
    fi
    local mysql_container_name
    mysql_container_name="$(docker ps "${compose_label_filters[@]}" --format '{{.Names}}' 2>/dev/null | head -n 1 || true)"

    if [ -z "${mysql_container_name}" ]; then
        warn "docker compose 라벨 기반 탐색 실패 — 컨테이너 이름 패턴(${MYSQL_SERVICE_NAME} 포함)으로 최종 폴백 시도. 공유 호스트에서는 다른 프로젝트 컨테이너를 오인할 위험이 있으니 가능하면 MYSQL_HOST 또는 COMPOSE_PROJECT_NAME을 명시적으로 지정할 것"
        mysql_container_name="$(docker ps --format '{{.Names}}' 2>/dev/null | grep -i "${MYSQL_SERVICE_NAME}" | head -n 1 || true)"
    fi
    if [ -z "${mysql_container_name}" ]; then
        warn "실행 중인 mysql 컨테이너를 찾지 못했습니다 — baseline SQL 적용 스킵"
        return 1
    fi

    if docker exec -i "${mysql_container_name}" mysql -u"${MYSQL_USER}" -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < "${BASELINE_SEED_FILE}"; then
        log "baseline SQL 적용 완료 (docker exec, container=${mysql_container_name})"
        return 0
    fi

    warn "docker exec 적용도 실패했습니다 — baseline SQL 적용 스킵"
    return 1
}

# ============================================================
# Step 2: B2B 파트너 발급 (②의 POST /api/admin/partners)
# ============================================================
provision_partner() {
    local existing_partner_api_key
    existing_partner_api_key="$(load_environment_variable PARTNER_API_KEY)"
    if [ -n "${existing_partner_api_key}" ] && [ "${FORCE_REPROVISION}" != "true" ]; then
        log "PARTNER_API_KEY가 이미 .env.sim에 있습니다 — 재발급 스킵(멱등). 강제 재발급은 FORCE_REPROVISION=true"
        return 0
    fi

    if [ -z "${ADMIN_EMAIL}" ] || [ -z "${ADMIN_PASSWORD}" ]; then
        warn "ADMIN_EMAIL/ADMIN_PASSWORD가 설정되지 않았습니다 — ADMIN 인증 불가, 파트너 발급 단계 스킵(부분 가용)"
        return 1
    fi

    log "ADMIN 로그인 시도: POST ${TARGET_URL}/auth/login"
    local login_response login_status_code login_response_body admin_access_token
    login_response="$(curl -sS -w '\n%{http_code}' -X POST "${TARGET_URL}/auth/login" \
        -H 'Content-Type: application/json' \
        -d "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\"}" 2>/dev/null || true)"
    login_status_code="$(echo "${login_response}" | tail -n 1)"
    login_response_body="$(echo "${login_response}" | sed '$d')"

    if [ "${login_status_code}" != "200" ]; then
        warn "ADMIN 로그인 실패(status=${login_status_code}) — 파트너 발급 단계 스킵(부분 가용). body=${login_response_body}"
        return 1
    fi

    admin_access_token="$(extract_json_string_field "${login_response_body}" accessToken)"
    if [ -z "${admin_access_token}" ]; then
        warn "ADMIN accessToken 파싱 실패 — 파트너 발급 단계 스킵. body=${login_response_body}"
        return 1
    fi

    log "파트너 생성 시도: POST ${TARGET_URL}/api/admin/partners (name=${PARTNER_NAME})"
    local partner_response partner_status_code partner_response_body plain_api_key
    partner_response="$(curl -sS -w '\n%{http_code}' -X POST "${TARGET_URL}/api/admin/partners" \
        -H "Authorization: Bearer ${admin_access_token}" \
        -H 'Content-Type: application/json' \
        -d "{\"name\":\"${PARTNER_NAME}\"}" 2>/dev/null || true)"
    partner_status_code="$(echo "${partner_response}" | tail -n 1)"
    partner_response_body="$(echo "${partner_response}" | sed '$d')"

    if [ "${partner_status_code}" = "404" ]; then
        warn "POST /api/admin/partners 404 — ② B2B 파트너 연동 기능이 미배포된 것으로 보입니다. 스킵(부분 가용)"
        return 1
    fi
    if [ "${partner_status_code}" != "201" ]; then
        warn "파트너 생성 실패(status=${partner_status_code}) — 스킵. body=${partner_response_body}"
        return 1
    fi

    plain_api_key="$(extract_json_string_field "${partner_response_body}" plainApiKey)"
    if [ -z "${plain_api_key}" ]; then
        warn "plainApiKey 파싱 실패 — 스킵. body=${partner_response_body}"
        return 1
    fi

    write_environment_variable PARTNER_API_KEY "${plain_api_key}"
    log "파트너 API Key 발급 완료 — .env.sim에 PARTNER_API_KEY 기록"
    return 0
}

# ============================================================
# Step 3: 마케팅 drop 개설 (③의 POST /limited-drops)
# ============================================================
provision_marketing_drop() {
    local existing_drop_id
    existing_drop_id="$(load_environment_variable DROP_ID)"
    if [ -n "${existing_drop_id}" ] && [ "${FORCE_REPROVISION}" != "true" ]; then
        log "DROP_ID가 이미 .env.sim에 있습니다 — 재개설 스킵(멱등). 강제 재개설은 FORCE_REPROVISION=true"
        return 0
    fi

    local open_at_timestamp close_at_timestamp
    open_at_timestamp="$(current_iso_timestamp)"
    close_at_timestamp="$(far_future_iso_timestamp)"

    log "한정판 drop 개설 시도: POST ${TARGET_URL}/limited-drops (productId=${DROP_PRODUCT_ID}, ownerUserId=${DROP_OWNER_USER_ID})"
    local drop_response drop_status_code drop_response_body drop_id
    drop_response="$(curl -sS -w '\n%{http_code}' -X POST "${TARGET_URL}/limited-drops" \
        -H "X-User-Id: ${DROP_OWNER_USER_ID}" \
        -H 'Content-Type: application/json' \
        -d "{\"productId\":${DROP_PRODUCT_ID},\"openAt\":\"${open_at_timestamp}\",\"closeAt\":\"${close_at_timestamp}\",\"limitedQuantity\":${DROP_LIMITED_QUANTITY},\"perUserLimit\":${DROP_PER_USER_LIMIT}}" 2>/dev/null || true)"
    drop_status_code="$(echo "${drop_response}" | tail -n 1)"
    drop_response_body="$(echo "${drop_response}" | sed '$d')"

    if [ "${drop_status_code}" = "404" ]; then
        warn "POST /limited-drops 404 — ③ 마케팅 이벤트 고부하 대응 기능이 미배포(limited-drop.enabled=false 등)된 것으로 보입니다. 스킵(부분 가용)"
        return 1
    fi
    if [ "${drop_status_code}" != "201" ]; then
        warn "한정판 drop 개설 실패(status=${drop_status_code}) — 스킵. body=${drop_response_body}"
        return 1
    fi

    drop_id="$(extract_json_number_field "${drop_response_body}" dropId)"
    if [ -z "${drop_id}" ]; then
        warn "dropId 파싱 실패 — 스킵. body=${drop_response_body}"
        return 1
    fi

    write_environment_variable DROP_ID "${drop_id}"
    log "한정판 drop 개설 완료(dropId=${drop_id}) — .env.sim에 DROP_ID 기록"
    return 0
}

# ============================================================
# Step 4: synthetic 범위 문서화 (.env.sim에 계약값 기록 — TDD "synthetic 격리 계약"과 동일)
# ============================================================
write_synthetic_range_documentation() {
    write_environment_variable QA_USER_POOL_SIZE "${QA_USER_POOL_SIZE}"
    write_environment_variable SYN_USER_ID_RANGE_START 900000
    write_environment_variable SYN_USER_ID_RANGE_END 999999
    write_environment_variable SYN_USER_EMAIL_DOMAIN loadtest.local
    write_environment_variable SYN_PRODUCT_ID_RANGE_START 9010001
    write_environment_variable SYN_PRODUCT_ID_RANGE_END 9010010
    write_environment_variable SYN_PRODUCT_OWNER_ID 1
    write_environment_variable SYN_FACILITY_IDS "SYN-FAC-1,SYN-FAC-2,SYN-FAC-3"
    write_environment_variable SYN_SLOT_ID_RANGE_START 9000001
    write_environment_variable SYN_SLOT_ID_RANGE_END 9000030
    write_environment_variable SYN_EVENT_ID 9000001
    write_environment_variable SYN_SEAT_ID_RANGE_START 9000001
    write_environment_variable SYN_SEAT_ID_RANGE_END 9005000
}

main() {
    log "=== INFRA-03 synthetic provision 시작 (TARGET_URL=${TARGET_URL}) ==="

    if apply_baseline_sql; then
        log "Step 1 완료: synthetic baseline SQL 적용"
    else
        warn "Step 1 스킵됨: synthetic baseline SQL 미적용 (위 로그 참고, 나머지 단계는 계속 진행)"
    fi

    if provision_partner; then
        log "Step 2 완료(또는 이미 완료됨): B2B 파트너 키"
    else
        warn "Step 2 스킵됨: B2B 파트너 키 미발급 (위 로그 참고, 나머지 단계는 계속 진행)"
    fi

    if provision_marketing_drop; then
        log "Step 3 완료(또는 이미 완료됨): 마케팅 drop"
    else
        warn "Step 3 스킵됨: 마케팅 drop 미개설 (위 로그 참고)"
    fi

    write_synthetic_range_documentation
    log "=== INFRA-03 synthetic provision 종료 (.env.sim: ${ENVIRONMENT_FILE}) ==="
}

main "$@"
