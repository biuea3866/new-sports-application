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
#   QA_API_URL              대상 API 베이스 URL. 기본값 http://localhost:8080(backend 직접 노출 포트).
#                           TDD Release Scenario 순서상 provision은 "2. provision" 단계로 LB 오버레이
#                           전환(3단계, docker-compose.lb.yml)보다 먼저 실행되므로 nginx-lb(8088)가
#                           아직 없을 수 있다 — qa/load/k6/lib/auth.js의 QA_API_URL 관례와 동일한
#                           기본값을 쓴다(레거시 TARGET_URL 환경변수를 넘기면 그 값을 그대로 쓴다).
#   MYSQL_HOST              설정 시 docker exec 대신 mysql 클라이언트로 직접 접속(dev 등 host 포트 노출 환경)
#   MYSQL_PORT              MYSQL_HOST 사용 시 포트. 기본값 3306
#   MYSQL_USER              MYSQL_HOST 사용 시 사용자. 기본값 root
#   MYSQL_PASSWORD          MYSQL_HOST 사용 시 비밀번호. 기본값 MYSQL_ROOT_PASSWORD 값
#   MYSQL_ROOT_PASSWORD     docker exec 경로에서 mysql 컨테이너 root 비밀번호. 기본값 root(compose 기본값과 일치)
#   MYSQL_DATABASE          대상 스키마. 기본값 sports
#   MYSQL_SERVICE_NAME      docker compose 서비스명. 기본값 mysql
#   COMPOSE_PROJECT_NAME    docker compose -p 프로젝트명(prod는 sports-prod 등). 미설정 시 compose 기본 추론에 위임
#   ADMIN_EMAIL/ADMIN_PASSWORD   ② 파트너 발급용 ADMIN 계정 로그인 정보. 미설정 시 파트너 발급 단계 스킵.
#                           로그인에 실패하면(계정 미존재) ensure_admin_account가 POST /users/register로
#                           등록 후 SQL로 ADMIN role을 직접 부여해 1회 한정 자가 부트스트랩한다
#                           (/admin/users/{id}/roles/{role}는 이미 ADMIN인 호출자를 요구하는 닭-달걀
#                           문제라 최초 ADMIN은 API만으로 만들 수 없다).
#   PARTNER_NAME            발급할 synthetic 파트너 표시명. 기본값 synthetic-load-partner
#   DROP_PRODUCT_ID         한정판 drop 대상 상품 id. 기본값 9010011(마케팅 drop 전용 상품 — B2C
#                           쓰기 곡선 공용 풀 9010001~9010010과 재고를 분리해 관측 왜곡을 막는다.
#                           qa/load/seeds/simulator-baseline.sql "Step 2-1" 참고)
#   DROP_OWNER_USER_ID      drop 개설 X-User-Id(상품 owner_id와 일치해야 함). 기본값 9000000(더미 owner —
#                           정찰 확정 사실: products/partners/owner는 id ≥ 9,000,000 범위에서만 실 row)
#   DROP_LIMITED_QUANTITY   drop 한정 수량. 기본값 100000000(스파이크 트래픽 전량 흡수)
#   DROP_PER_USER_LIMIT     1인 구매 한도. 기본값 1000000(부하 스크립트가 한도 초과로 막히지 않게 크게)
#   FORCE_REPROVISION       true면 기존 .env.sim의 PARTNER_API_KEY/DROP_ID를 무시하고 재발급 시도
#   QA_USER_POOL_SIZE       유저 풀 크기(문서화 목적 — X-User-Id 헤더 모델이라 실제 유저 row 생성 불요)
#
# 안전장치:
#   - set -u로 미정의 변수 참조를 즉시 실패시키고, 스킵이 의도된 지점만 명시적으로 `|| true`로 감싼다.
#   - assert_safe_target으로 QA_API_URL이 localhost/127.0.0.1/*.local이 아니면 즉시 중단한다
#     (qa/load/k6/lib/auth.js#assertSafeTarget과 동일 규칙 — 이 스크립트는 실제 계정을 등록하고
#     ADMIN 권한을 부여하므로 운영·staging 오조준 시 피해가 부하 스크립트보다 크다).

set -u
set -o pipefail

# ---- 경로 계산 ----
SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIRECTORY}/../../.." && pwd)"
BASELINE_SEED_FILE="${REPOSITORY_ROOT}/qa/load/seeds/simulator-baseline.sql"
ENVIRONMENT_FILE="${REPOSITORY_ROOT}/qa/load/.env.sim"

# ---- 환경 변수 기본값 ----
QA_API_URL="${QA_API_URL:-${TARGET_URL:-http://localhost:8080}}"
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
DROP_PRODUCT_ID="${DROP_PRODUCT_ID:-9010011}"
DROP_OWNER_USER_ID="${DROP_OWNER_USER_ID:-9000000}"
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

# ---- 안전장치: 운영·staging 오조준 차단 (qa/load/k6/lib/auth.js#assertSafeTarget과 동일 규칙) ----
assert_safe_target() {
    if [[ "${QA_API_URL}" =~ ^https?://(localhost|127\.0\.0\.1|[^/]*\.local)(:|/|$) ]]; then
        return 0
    fi
    warn "[SAFETY] QA_API_URL=${QA_API_URL}는 로컬 대상이 아닙니다. 이 스크립트는 계정을 등록하고" \
         "ADMIN 권한을 부여하므로 운영·staging 부하는 별도 승인 없이 실행할 수 없습니다."
    exit 1
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
# 공통: mysql 접속 경로 탐색 + SQL 실행 (baseline 파일 적용과 ADMIN role 부여 SQL이 공유)
#   resolve_mysql_target: "host" | "compose" | "docker:<container_name>" | ""(실패) 를 표준출력으로 반환
#   exec_mysql_stdin:     stdin으로 받은 SQL을 위 target에 맞춰 실행
# ============================================================
resolve_mysql_target() {
    if [ -n "${MYSQL_HOST}" ]; then
        if command -v mysql >/dev/null 2>&1; then
            echo "host"
            return 0
        fi
        warn "MYSQL_HOST=${MYSQL_HOST} 설정됐지만 mysql 클라이언트가 설치돼 있지 않습니다"
        return 1
    fi

    local compose_project_flag=()
    if [ -n "${COMPOSE_PROJECT_NAME}" ]; then
        compose_project_flag=(-p "${COMPOSE_PROJECT_NAME}")
    fi
    if (cd "${REPOSITORY_ROOT}" && docker compose "${compose_project_flag[@]+"${compose_project_flag[@]}"}" exec -T "${MYSQL_SERVICE_NAME}" true) 2>/dev/null; then
        echo "compose"
        return 0
    fi

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
    if [ -n "${mysql_container_name}" ]; then
        echo "docker:${mysql_container_name}"
        return 0
    fi

    return 1
}

exec_mysql_stdin() {
    local target="$1"
    case "${target}" in
        host)
            mysql -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${MYSQL_DATABASE}"
            ;;
        compose)
            local compose_project_flag=()
            if [ -n "${COMPOSE_PROJECT_NAME}" ]; then
                compose_project_flag=(-p "${COMPOSE_PROJECT_NAME}")
            fi
            (cd "${REPOSITORY_ROOT}" && docker compose "${compose_project_flag[@]+"${compose_project_flag[@]}"}" exec -T "${MYSQL_SERVICE_NAME}" \
                mysql -u"${MYSQL_USER}" -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}")
            ;;
        docker:*)
            docker exec -i "${target#docker:}" mysql -u"${MYSQL_USER}" -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}"
            ;;
        *)
            return 1
            ;;
    esac
}

# ============================================================
# Step 1: synthetic baseline SQL 적용
# ============================================================
apply_baseline_sql() {
    if [ ! -f "${BASELINE_SEED_FILE}" ]; then
        warn "시드 파일을 찾을 수 없습니다: ${BASELINE_SEED_FILE} — baseline SQL 적용 스킵"
        return 1
    fi

    local mysql_target
    mysql_target="$(resolve_mysql_target)"
    if [ -z "${mysql_target}" ]; then
        warn "mysql 접속 경로를 찾지 못했습니다(MYSQL_HOST 미설정 + docker compose/실행 컨테이너 탐색 실패) — baseline SQL 적용 스킵"
        return 1
    fi

    if exec_mysql_stdin "${mysql_target}" < "${BASELINE_SEED_FILE}"; then
        log "baseline SQL 적용 완료 (${mysql_target})"
        return 0
    fi

    warn "baseline SQL 적용 실패 (${mysql_target})"
    return 1
}

# ============================================================
# Step 2 사전 단계: ADMIN 로그인 시도 (성공 시 accessToken 출력, 실패 시 빈 문자열 + 비0 종료코드)
# ============================================================
try_admin_login() {
    local login_response login_status_code login_response_body admin_access_token
    login_response="$(curl -sS -w '\n%{http_code}' -X POST "${QA_API_URL}/auth/login" \
        -H 'Content-Type: application/json' \
        -d "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\"}" 2>/dev/null || true)"
    login_status_code="$(echo "${login_response}" | tail -n 1)"
    login_response_body="$(echo "${login_response}" | sed '$d')"

    if [ "${login_status_code}" != "200" ]; then
        return 1
    fi
    admin_access_token="$(extract_json_string_field "${login_response_body}" accessToken)"
    if [ -z "${admin_access_token}" ]; then
        return 1
    fi
    echo "${admin_access_token}"
    return 0
}

# ============================================================
# Step 2 사전 단계: ADMIN 계정 자가 부트스트랩(멱등)
#   /admin/users/{id}/roles/{role}는 이미 ADMIN인 호출자를 요구해(닭-달걀 문제) API만으로는
#   최초 ADMIN을 만들 수 없다 — 로그인이 이미 성공하면 아무 것도 하지 않고(멱등),
#   실패한 경우에만 REGISTER(공개 API) + ADMIN role 부여(직접 SQL)로 1회 부트스트랩한다.
# ============================================================
ensure_admin_account() {
    if [ -z "${ADMIN_EMAIL}" ] || [ -z "${ADMIN_PASSWORD}" ]; then
        return 1
    fi

    if try_admin_login >/dev/null; then
        log "ADMIN 계정 로그인 확인됨(${ADMIN_EMAIL}) — 부트스트랩 불필요(멱등)"
        return 0
    fi

    log "ADMIN 계정 로그인 실패 — 신규 부트스트랩 시도(POST /users/register + SQL role 부여): ${ADMIN_EMAIL}"
    local register_response register_status_code
    register_response="$(curl -sS -w '\n%{http_code}' -X POST "${QA_API_URL}/users/register" \
        -H 'Content-Type: application/json' \
        -d "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\"}" 2>/dev/null || true)"
    register_status_code="$(echo "${register_response}" | tail -n 1)"

    if [ "${register_status_code}" != "201" ]; then
        warn "ADMIN 계정 register 실패(status=${register_status_code}) — 이미 존재하는 이메일인데 비밀번호가 다르거나 users/register가 배포되지 않은 것으로 보입니다. 부트스트랩 중단"
        return 1
    fi

    local mysql_target
    mysql_target="$(resolve_mysql_target)"
    if [ -z "${mysql_target}" ]; then
        warn "mysql 접속 경로가 없어 ADMIN role 부여 SQL을 실행할 수 없습니다 — 부트스트랩 중단(계정은 USER role로 등록된 상태로 남습니다)"
        return 1
    fi

    # roles.id는 환경마다 auto_increment 값이 달라질 수 있어 이름으로 조회한다.
    # user_roles 고유키(user_id, role_id, deleted_at)는 deleted_at이 NULL이면 MySQL이 중복을
    # 막지 못하므로(널은 유일성 비교에서 항상 다른 값으로 취급) NOT EXISTS로 직접 멱등을 보장한다.
    local grant_admin_role_sql="INSERT INTO user_roles (user_id, role_id, created_at, updated_at)
SELECT u.id, r.id, NOW(6), NOW(6)
FROM users u JOIN roles r ON r.name = 'ADMIN'
WHERE u.email = '${ADMIN_EMAIL}'
  AND NOT EXISTS (
    SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id AND ur.deleted_at IS NULL
  );"
    if ! echo "${grant_admin_role_sql}" | exec_mysql_stdin "${mysql_target}"; then
        warn "ADMIN role 부여 SQL 실행 실패(${mysql_target}) — 부트스트랩 중단"
        return 1
    fi

    if try_admin_login >/dev/null; then
        log "ADMIN 계정 부트스트랩 완료(${ADMIN_EMAIL})"
        return 0
    fi
    warn "ADMIN role 부여 후에도 로그인 확인 실패 — 부트스트랩 중단"
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

    ensure_admin_account || true

    log "ADMIN 로그인 시도: POST ${QA_API_URL}/auth/login"
    local admin_access_token
    admin_access_token="$(try_admin_login)"
    if [ -z "${admin_access_token}" ]; then
        warn "ADMIN 로그인 실패 — 파트너 발급 단계 스킵(부분 가용)"
        return 1
    fi

    log "파트너 생성 시도: POST ${QA_API_URL}/api/admin/partners (name=${PARTNER_NAME})"
    local partner_response partner_status_code partner_response_body plain_api_key
    partner_response="$(curl -sS -w '\n%{http_code}' -X POST "${QA_API_URL}/api/admin/partners" \
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

    log "한정판 drop 개설 시도: POST ${QA_API_URL}/limited-drops (productId=${DROP_PRODUCT_ID}, ownerUserId=${DROP_OWNER_USER_ID})"
    local drop_response drop_status_code drop_response_body drop_id
    drop_response="$(curl -sS -w '\n%{http_code}' -X POST "${QA_API_URL}/limited-drops" \
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
    write_environment_variable SYN_MARKETING_DROP_PRODUCT_ID 9010011
    write_environment_variable SYN_PRODUCT_OWNER_ID 9000000
    write_environment_variable SYN_FACILITY_IDS "SYN-FAC-1,SYN-FAC-2,SYN-FAC-3"
    write_environment_variable SYN_SLOT_ID_RANGE_START 9000001
    write_environment_variable SYN_SLOT_ID_RANGE_END 9000030
    write_environment_variable SYN_EVENT_ID 9000001
    write_environment_variable SYN_SEAT_ID_RANGE_START 9000001
    write_environment_variable SYN_SEAT_ID_RANGE_END 9005000
}

main() {
    assert_safe_target
    log "=== INFRA-03 synthetic provision 시작 (QA_API_URL=${QA_API_URL}) ==="

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
