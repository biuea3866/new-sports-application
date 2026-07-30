#!/usr/bin/env bash
# qa/load/verify-limited-drop.sh
#
# FIX-01: 한정판 구매 결함(F1 예약-주문 불일치/누수, F3 DB커밋-클라이언트응답 불일치) 재현 검증 하네스.
# 근거 티켓: 프로젝트/스포츠앱/MSA 물리분리/Tickets/FIX-01-한정판-예약누수-재현검증-하네스-복원.md
# 근거 실측: 프로젝트/스포츠앱/상시 트래픽 시뮬레이터/실측-리포트.md L60(측정 조건)·L64-69(관측표)·
#           L77-83(HikariCP 로그)·L90(F1)·L92(F3)
#
# 세 판정을 산출한다 (전부 순수 함수 judge_* — 인프라 없이 --self-test로 독립 검증 가능):
#   1. 누수(F1)          = 예약 마커 수 - 주문 수        (양수면 실패)
#   2. 응답 불일치(F3)   = 주문 수 - k6 202 응답 수       (양수면 실패)
#   3. 오버셀(회귀 보호) = 주문 수 - limitedQuantity      (양수면 실패)
#
# 예약 키 열거는 SCAN만 사용한다 — KEYS 명령 금지(private-redis-convention "키 설계").
#
# 사용법 (실측 재현 예시):
#   1) k6 실행 — dropId는 setup()의 console.log(`[LOAD-05] dropId=...`) 출력에서 파싱한다.
#      QA_API_URL=http://localhost:18080 QA_JWT_SECRET=<백엔드 app.jwt.secret과 동일 값> \
#        QA_DROP_EXECUTOR=constant-arrival-rate QA_DROP_RATE=400 QA_DROP_DURATION=30s \
#        QA_LIMITED_DROP_QUANTITY=2000 \
#        k6 run --summary-export=results/goods-limited-drop-summary.json \
#          k6/goods-limited-drop-spike.js 2>&1 | tee results/goods-limited-drop-run.log
#
#   2) 검증 — ORDER_SINCE_TIMESTAMP는 k6 실행 직전(위 1번 시작 전) 시각을 넣는다(같은 product를
#      재사용하는 반복 실행에서 이전 회차 주문이 섞이는 것을 막기 위해 필수 — FIX-01 실측 중 실제로
#      재현됨: 동일 product_id에 이전 실행의 주문이 누적되어 있었다).
#      DROP_ID=$(grep -oE 'dropId=[0-9]+' results/goods-limited-drop-run.log | tail -1 | cut -d= -f2) \
#      PRODUCT_ID=9000001 \
#      LIMITED_QUANTITY=2000 \
#      K6_SUMMARY_JSON=results/goods-limited-drop-summary.json \
#      ORDER_SINCE_TIMESTAMP="2026-07-30 09:00:00" \
#        ./qa/load/verify-limited-drop.sh
#
# 환경 변수:
#   DROP_ID                (필수, --self-test 제외) 검증 대상 한정판 회차 id
#   PRODUCT_ID              (필수) 회차 상품 id — goods_orders는 drop_id를 보유하지 않으므로
#                           goods_order_items.product_id 조인으로 스코프를 좁힌다
#   LIMITED_QUANTITY        (필수) 오버셀 판정 기준 한정 수량
#   K6_SUMMARY_JSON          (필수) k6 --summary-export 산출 JSON 경로
#   ORDER_SINCE_TIMESTAMP    (필수) 이 시각(“YYYY-MM-DD HH:MM:SS”, DATETIME 리터럴) 이후 생성된
#                           주문만 카운트 — 같은 product를 재사용하는 반복 실행의 주문 오염 방지
#   ACCEPTED_METRIC_NAME     (선택) k6 202 카운터 메트릭명. 기본값 LOAD_05_accepted_total
#                           (goods-limited-drop-spike.js#acceptedCounter)
#   MYSQL_HOST/PORT/USER/PASSWORD/ROOT_PASSWORD/DATABASE/SERVICE_NAME/COMPOSE_PROJECT_NAME
#                           qa/load/provision/provision.sh와 동일 접속 계열(호스트 직접 접속 우선 →
#                           docker compose exec 폴백 → 라벨 기반 컨테이너 탐색 최종 폴백)
#   REDIS_HOST/PORT/SERVICE_NAME
#                           qa/load/reseed/reseed.sh와 동일 접속 계열
#
# 종료 코드:
#   0  모든 판정 통과(누수 0·응답 불일치 0·오버셀 없음)
#   1  하나 이상 판정 실패(누수>0 또는 응답 불일치>0 또는 오버셀>0)
#   2  측정 불가(예약 키 0건 — 누수 0으로 오판하지 않기 위한 별도 코드. DROP_ID 오류·Redis 키
#      만료·drop 미생성 등 실제 원인 확인 필요)
#
# --self-test: 판정 함수 3종을 티켓 "테스트 케이스" 8개 샘플 입력으로 재현하고 종료한다
#              (인프라 불필요 — MySQL/Redis/k6 산출물 없이 로직만 검증).

set -u
set -o pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIRECTORY}/../.." && pwd)"

# ---- 환경 변수 기본값 (MySQL/Redis 접속 계열은 provision.sh·reseed.sh와 동일 관례) ----
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

DROP_ID="${DROP_ID:-}"
PRODUCT_ID="${PRODUCT_ID:-}"
LIMITED_QUANTITY="${LIMITED_QUANTITY:-}"
K6_SUMMARY_JSON="${K6_SUMMARY_JSON:-}"
ORDER_SINCE_TIMESTAMP="${ORDER_SINCE_TIMESTAMP:-}"
ACCEPTED_METRIC_NAME="${ACCEPTED_METRIC_NAME:-LOAD_05_accepted_total}"

log() { echo "[verify-limited-drop] $*"; }
warn() { echo "[verify-limited-drop][WARN] $*" >&2; }

# ============================================================
# 판정 함수 (순수 함수 — stdout에 차이값, 종료코드로 통과/실패) — 티켓 "테스트 케이스" 대응
# ============================================================

# "예약 마커 수와 주문 수가 같으면 누수 0건 판정" / "예약 마커 수가 주문 수보다 많으면 누수 리포트"
judge_leak() {
    local reserved_count="$1"
    local order_count="$2"
    local leak=$((reserved_count - order_count))
    if [ "${leak}" -gt 0 ]; then
        echo "${leak}"
        return 1
    fi
    echo "0"
    return 0
}

# "주문 수가 k6 202 카운트보다 많으면 응답 불일치 건수를 리포트"
judge_response_mismatch() {
    local order_count="$1"
    local accepted_count="$2"
    local mismatch=$((order_count - accepted_count))
    if [ "${mismatch}" -gt 0 ]; then
        echo "${mismatch}"
        return 1
    fi
    echo "0"
    return 0
}

# "주문 수가 한정 수량을 초과하면 오버셀로 판정" (기존 게이트 회귀 보호)
judge_oversell() {
    local order_count="$1"
    local limited_quantity="$2"
    local over=$((order_count - limited_quantity))
    if [ "${over}" -gt 0 ]; then
        echo "${over}"
        return 1
    fi
    echo "0"
    return 0
}

# "대상 drop의 예약 키가 0건이면 측정 불가로 판정하고 누수 0으로 오판하지 않는다" (엣지)
is_leak_measurable() {
    local reserved_count="$1"
    [ "${reserved_count}" -gt 0 ]
}

# ============================================================
# --self-test: 인프라 없이 판정 함수 3종을 티켓 샘플 입력으로 재현
# ============================================================
run_self_test() {
    local self_test_failures=0

    log "=== --self-test 시작 (티켓 '테스트 케이스' 재현, 인프라 불요) ==="

    # 1) 예약 마커 수와 주문 수가 같으면 누수 0건, 종료코드 0
    local leak_output leak_exit
    leak_output="$(judge_leak 2000 2000)"; leak_exit=$?
    if [ "${leak_output}" = "0" ] && [ "${leak_exit}" -eq 0 ]; then
        log "PASS: judge_leak(2000,2000) -> leak=${leak_output} exit=${leak_exit} (기대: 0/0)"
    else
        warn "FAIL: judge_leak(2000,2000) -> leak=${leak_output} exit=${leak_exit} (기대: 0/0)"
        self_test_failures=$((self_test_failures + 1))
    fi

    # 2) 예약 마커 수가 주문 수보다 많으면 누수 건수를 차이값으로, 비-0 종료코드
    #    (실측-리포트.md L64-69: reserved=2000, goods_orders=1357 → leak=643)
    leak_output="$(judge_leak 2000 1357)"; leak_exit=$?
    if [ "${leak_output}" = "643" ] && [ "${leak_exit}" -eq 1 ]; then
        log "PASS: judge_leak(2000,1357) -> leak=${leak_output} exit=${leak_exit} (기대: 643/1)"
    else
        warn "FAIL: judge_leak(2000,1357) -> leak=${leak_output} exit=${leak_exit} (기대: 643/1)"
        self_test_failures=$((self_test_failures + 1))
    fi

    # 3) 주문 수가 k6 202 카운트보다 많으면 응답 불일치 건수를 리포트, 비-0 종료코드
    #    (실측-리포트.md L92: goods_orders=1357, 202=228 → mismatch=1129)
    local mismatch_output mismatch_exit
    mismatch_output="$(judge_response_mismatch 1357 228)"; mismatch_exit=$?
    if [ "${mismatch_output}" = "1129" ] && [ "${mismatch_exit}" -eq 1 ]; then
        log "PASS: judge_response_mismatch(1357,228) -> mismatch=${mismatch_output} exit=${mismatch_exit} (기대: 1129/1)"
    else
        warn "FAIL: judge_response_mismatch(1357,228) -> mismatch=${mismatch_output} exit=${mismatch_exit} (기대: 1129/1)"
        self_test_failures=$((self_test_failures + 1))
    fi

    # 3-1) 응답 불일치 없음(주문 수 <= 202 카운트)이면 통과
    mismatch_output="$(judge_response_mismatch 200 228)"; mismatch_exit=$?
    if [ "${mismatch_output}" = "0" ] && [ "${mismatch_exit}" -eq 0 ]; then
        log "PASS: judge_response_mismatch(200,228) -> mismatch=${mismatch_output} exit=${mismatch_exit} (기대: 0/0)"
    else
        warn "FAIL: judge_response_mismatch(200,228) -> mismatch=${mismatch_output} exit=${mismatch_exit} (기대: 0/0)"
        self_test_failures=$((self_test_failures + 1))
    fi

    # 4) 주문 수가 한정 수량을 초과하면 오버셀로 판정
    local oversell_output oversell_exit
    oversell_output="$(judge_oversell 2001 2000)"; oversell_exit=$?
    if [ "${oversell_output}" = "1" ] && [ "${oversell_exit}" -eq 1 ]; then
        log "PASS: judge_oversell(2001,2000) -> over=${oversell_output} exit=${oversell_exit} (기대: 1/1)"
    else
        warn "FAIL: judge_oversell(2001,2000) -> over=${oversell_output} exit=${oversell_exit} (기대: 1/1)"
        self_test_failures=$((self_test_failures + 1))
    fi

    # 4-1) 주문 수가 한정 수량 이하면 오버셀 없음
    oversell_output="$(judge_oversell 2000 2000)"; oversell_exit=$?
    if [ "${oversell_output}" = "0" ] && [ "${oversell_exit}" -eq 0 ]; then
        log "PASS: judge_oversell(2000,2000) -> over=${oversell_output} exit=${oversell_exit} (기대: 0/0)"
    else
        warn "FAIL: judge_oversell(2000,2000) -> over=${oversell_output} exit=${oversell_exit} (기대: 0/0)"
        self_test_failures=$((self_test_failures + 1))
    fi

    # 5) 대상 drop의 예약 키가 0건이면 측정 불가로 판정 — 누수 0으로 오판하지 않는다(엣지)
    if is_leak_measurable 0; then
        warn "FAIL: is_leak_measurable(0) -> true (기대: false/측정 불가)"
        self_test_failures=$((self_test_failures + 1))
    else
        log "PASS: is_leak_measurable(0) -> false (측정 불가로 판정, 누수 0 오판 방지)"
    fi
    if is_leak_measurable 5; then
        log "PASS: is_leak_measurable(5) -> true (정상 측정 가능)"
    else
        warn "FAIL: is_leak_measurable(5) -> false (기대: true)"
        self_test_failures=$((self_test_failures + 1))
    fi

    # 6) 예약 키 열거에 KEYS를 사용하지 않는다(SCAN만) — 이 스크립트 자신의 소스를 grep으로 검증
    if grep -nE '(^|[^A-Za-z_])redis_exec[[:space:]]+KEYS([^A-Za-z_]|$)' "${BASH_SOURCE[0]}" >/dev/null 2>&1; then
        warn "FAIL: 이 스크립트가 Redis KEYS 명령을 사용합니다(SCAN만 허용)"
        self_test_failures=$((self_test_failures + 1))
    else
        log "PASS: Redis KEYS 명령 미사용 확인(SCAN 기반 count_keys_matching만 사용)"
    fi

    log "=== --self-test 종료: 실패 ${self_test_failures}건 ==="
    if [ "${self_test_failures}" -gt 0 ]; then
        return 1
    fi
    return 0
}

# ============================================================
# MySQL 접속 — provision.sh#resolve_mysql_target / exec_mysql_stdin과 동일 3단 폴백
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

    local compose_label_filters=(--filter "label=com.docker.compose.service=${MYSQL_SERVICE_NAME}")
    if [ -n "${COMPOSE_PROJECT_NAME}" ]; then
        compose_label_filters+=(--filter "label=com.docker.compose.project=${COMPOSE_PROJECT_NAME}")
    fi
    local mysql_container_name
    mysql_container_name="$(docker ps "${compose_label_filters[@]}" --format '{{.Names}}' 2>/dev/null | head -n 1 || true)"
    if [ -z "${mysql_container_name}" ]; then
        warn "docker compose 라벨 기반 탐색 실패 — 컨테이너 이름 패턴(${MYSQL_SERVICE_NAME} 포함)으로 최종 폴백 시도"
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
            mysql -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" -N -B "${MYSQL_DATABASE}"
            ;;
        compose)
            local compose_project_flag=()
            if [ -n "${COMPOSE_PROJECT_NAME}" ]; then
                compose_project_flag=(-p "${COMPOSE_PROJECT_NAME}")
            fi
            (cd "${REPOSITORY_ROOT}" && docker compose "${compose_project_flag[@]+"${compose_project_flag[@]}"}" exec -T "${MYSQL_SERVICE_NAME}" \
                mysql -u"${MYSQL_USER}" -p"${MYSQL_ROOT_PASSWORD}" -N -B "${MYSQL_DATABASE}")
            ;;
        docker:*)
            docker exec -i "${target#docker:}" mysql -u"${MYSQL_USER}" -p"${MYSQL_ROOT_PASSWORD}" -N -B "${MYSQL_DATABASE}"
            ;;
        *)
            return 1
            ;;
    esac
}

# ============================================================
# Redis 접속 — reseed.sh#redis_exec와 동일 폴백
# ============================================================
redis_exec() {
    if [ -n "${REDIS_HOST}" ]; then
        if ! command -v redis-cli >/dev/null 2>&1; then
            warn "redis-cli가 설치돼 있지 않습니다"
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
        warn "실행 중인 redis 컨테이너를 찾지 못했습니다"
        return 1
    fi
    docker exec -i "${redis_container_name}" redis-cli "$@"
}

# SCAN 기반 키 카운트(KEYS 명령 금지 — private-redis-convention). 매칭 키가 없으면 0을 반환한다.
count_keys_matching() {
    local pattern="$1"
    local cursor="0"
    local matched_count=0
    while :; do
        local scan_result
        # 비-tty 파이프 실행 시 redis-cli는 raw 모드로 "커서\n키1\n키2..." 평평한 줄 단위 출력을 낸다.
        scan_result="$(redis_exec SCAN "${cursor}" MATCH "${pattern}" COUNT 200 2>/dev/null || true)"
        if [ -z "${scan_result}" ]; then
            break
        fi
        cursor="$(echo "${scan_result}" | sed -n '1p' | tr -d '"')"
        local matched_keys
        matched_keys="$(echo "${scan_result}" | tail -n +2 | tr -d '"')"
        if [ -n "${matched_keys}" ]; then
            local line_count
            line_count="$(echo "${matched_keys}" | grep -c . || true)"
            matched_count=$((matched_count + line_count))
        fi
        if [ "${cursor}" = "0" ]; then
            break
        fi
    done
    echo "${matched_count}"
}

# ============================================================
# 실측정: Redis 예약 마커 수 — DropReservationStoreImpl.kt#reservedKey와 동일 키 패턴
#   ("goods:limited-drop:{dropId}:reserved:{idempotencyKey}", :185)
# ============================================================
measure_reserved_count() {
    count_keys_matching "goods:limited-drop:${DROP_ID}:reserved:*"
}

# remaining 키(DropReservationStoreImpl.kt#remainingKey, :181) — 복원 여부 추적용, 판정에는 미사용.
measure_remaining() {
    redis_exec GET "goods:limited-drop:${DROP_ID}:remaining" 2>/dev/null | tr -d '\r'
}

# ============================================================
# 실측정: DB 주문 수 — goods_orders는 drop_id를 보유하지 않으므로 goods_order_items.product_id로
#   조인해 스코프를 좁히고, ORDER_SINCE_TIMESTAMP로 이전 실행의 주문 오염을 배제한다.
# ============================================================
measure_order_count() {
    local mysql_target="$1"
    local query="SELECT COUNT(DISTINCT o.id) FROM goods_orders o
JOIN goods_order_items i ON i.order_id = o.id
WHERE i.product_id = ${PRODUCT_ID}
  AND o.deleted_at IS NULL
  AND o.created_at >= '${ORDER_SINCE_TIMESTAMP}';"
    echo "${query}" | exec_mysql_stdin "${mysql_target}" | tr -d '\r'
}

# ============================================================
# 실측정: k6 202 카운터 — --summary-export JSON의 metrics.<name>.count
# ============================================================
measure_accepted_count() {
    if ! command -v jq >/dev/null 2>&1; then
        warn "jq가 설치돼 있지 않습니다 — k6 summary JSON 파싱 불가"
        return 1
    fi
    if [ ! -f "${K6_SUMMARY_JSON}" ]; then
        warn "K6_SUMMARY_JSON 파일을 찾을 수 없습니다: ${K6_SUMMARY_JSON}"
        return 1
    fi
    jq -r --arg name "${ACCEPTED_METRIC_NAME}" '.metrics[$name].count // 0' "${K6_SUMMARY_JSON}"
}

# ============================================================
# 메인 검증 흐름
# ============================================================
main() {
    if [ -z "${DROP_ID}" ] || [ -z "${PRODUCT_ID}" ] || [ -z "${LIMITED_QUANTITY}" ] \
        || [ -z "${K6_SUMMARY_JSON}" ] || [ -z "${ORDER_SINCE_TIMESTAMP}" ]; then
        warn "필수 환경 변수 누락: DROP_ID, PRODUCT_ID, LIMITED_QUANTITY, K6_SUMMARY_JSON, ORDER_SINCE_TIMESTAMP 모두 필요합니다"
        warn "사용법은 스크립트 상단 주석 참고, 또는 --self-test로 판정 로직만 검증하세요"
        exit 2
    fi

    log "=== FIX-01 한정판 재현 검증 시작 (dropId=${DROP_ID}, productId=${PRODUCT_ID}, limitedQuantity=${LIMITED_QUANTITY}) ==="

    local mysql_target
    mysql_target="$(resolve_mysql_target)"
    if [ -z "${mysql_target}" ]; then
        warn "MySQL 접속 경로를 찾지 못했습니다(MYSQL_HOST 미설정 + docker compose/실행 컨테이너 탐색 실패) — 검증 불가"
        exit 2
    fi
    log "MySQL 접속 경로: ${mysql_target}"

    local reserved_count remaining_value order_count accepted_count
    reserved_count="$(measure_reserved_count)"
    remaining_value="$(measure_remaining)"
    order_count="$(measure_order_count "${mysql_target}")"
    if ! accepted_count="$(measure_accepted_count)"; then
        warn "k6 202 카운터를 읽지 못했습니다 — 검증 불가"
        exit 2
    fi

    log "관측값: reserved(Redis)=${reserved_count} remaining(Redis)=${remaining_value} order(DB)=${order_count} accepted(k6 202)=${accepted_count}"

    local overall_exit=0

    if ! is_leak_measurable "${reserved_count}"; then
        warn "측정 불가: dropId=${DROP_ID}의 예약 키(goods:limited-drop:${DROP_ID}:reserved:*)가 0건입니다 — " \
             "누수 0건으로 오판하지 않습니다. dropId·TTL 만료·drop 미생성 여부를 확인하세요."
        overall_exit=2
    else
        local leak_output leak_exit
        leak_output="$(judge_leak "${reserved_count}" "${order_count}")"; leak_exit=$?
        if [ "${leak_exit}" -eq 0 ]; then
            log "판정 1/3 누수(F1): 0건 통과 (reserved=${reserved_count}, order=${order_count})"
        else
            warn "판정 1/3 누수(F1): ${leak_output}건 실패 (reserved=${reserved_count} - order=${order_count})"
            overall_exit=1
        fi
    fi

    local mismatch_output mismatch_exit
    mismatch_output="$(judge_response_mismatch "${order_count}" "${accepted_count}")"; mismatch_exit=$?
    if [ "${mismatch_exit}" -eq 0 ]; then
        log "판정 2/3 응답 불일치(F3): 0건 통과 (order=${order_count}, accepted=${accepted_count})"
    else
        warn "판정 2/3 응답 불일치(F3): ${mismatch_output}건 실패 (order=${order_count} - accepted=${accepted_count})"
        overall_exit=1
    fi

    local oversell_output oversell_exit
    oversell_output="$(judge_oversell "${order_count}" "${LIMITED_QUANTITY}")"; oversell_exit=$?
    if [ "${oversell_exit}" -eq 0 ]; then
        log "판정 3/3 오버셀(회귀 보호): 없음 통과 (order=${order_count}, limitedQuantity=${LIMITED_QUANTITY})"
    else
        warn "판정 3/3 오버셀(회귀 보호): ${oversell_output}건 실패 — 게이트 회귀! (order=${order_count} - limitedQuantity=${LIMITED_QUANTITY})"
        overall_exit=1
    fi

    log "=== 검증 종료 (종료코드 ${overall_exit}) ==="
    exit "${overall_exit}"
}

if [ "${1:-}" = "--self-test" ]; then
    run_self_test
    exit $?
fi

main "$@"
