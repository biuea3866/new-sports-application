#!/usr/bin/env bash
# qa/load/verify-limited-drop.sh
#
# FIX-01: 한정판 구매 결함(F1 예약-주문 불일치/누수, F3 DB커밋-클라이언트응답 불일치) 재현 검증 하네스.
# 근거 티켓: 프로젝트/스포츠앱/MSA 물리분리/Tickets/FIX-01-한정판-예약누수-재현검증-하네스-복원.md
# 근거 실측: 프로젝트/스포츠앱/상시 트래픽 시뮬레이터/실측-리포트.md L60(측정 조건)·L64-69(관측표)·
#           L77-83(HikariCP 로그)·L90(F1)·L92(F3)
#
# 세 판정을 산출한다 (전부 순수 함수 judge_* — 인프라 없이 --self-test로 독립 검증 가능):
#   1. 누수(F1)          = 예약 마커 수 - 주문 수  (양수=누수 실패 exit 1 / 0=통과 exit 0 /
#                          음수=측정 불가 exit 2 — 예약 마커 TTL 만료(app.limited-drop.reservation.
#                          marker-ttl-seconds 기본 600초. DropReservationStoreImpl.kt#confirmSuccess가
#                          no-op이라 성공 건 마커도 남아 시간 경과 시 만료된다) 또는 DROP_ID 오기입 의심)
#   2. 응답 불일치(F3)   = 주문 수 - k6 202 응답 수 (양수=응답 불일치 실패 exit 1 / 0=통과 exit 0 /
#                          음수=유령 성공 실패 exit 1 — 202는 받았는데 DB 주문이 없음. F3와 원인이 달라
#                          별도 메시지로 리포트한다. ORDER_SINCE_TIMESTAMP 측정 구간 오설정으로도 발생 가능)
#   3. 오버셀(회귀 보호) = 주문 수 - limitedQuantity      (양수면 실패)
#
# 측정값(Redis/MySQL/k6 summary 조회 결과)은 정수 검증을 거친다 — 조회 실패로 빈 문자열이 반환되면
# bash 산술이 0으로 강제 변환해 인프라 실패를 "누수 0건/불일치 0건"으로 오판할 수 있기 때문이다.
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
#                           주문만 카운트 — 같은 product를 재사용하는 반복 실행의 주문 오염 방지.
#                           **MySQL 서버 세션 타임존 기준으로 해석된다(UTC 아님)** — 로컬/UTC와 서버
#                           타임존이 다르면 리터럴을 서버 타임존에 맞춰 넣거나, k6 실행 직전 상대 구간
#                           (예: `date -v-5M '+%Y-%m-%d %H:%M:%S'`)으로 계산해 오차를 없애라
#   ACCEPTED_METRIC_NAME     (선택) k6 202 카운터 메트릭명. 기본값 LOAD_05_accepted_total
#                           (goods-limited-drop-spike.js#acceptedCounter)
#   REQUESTS_METRIC_NAME     (선택) k6 총 요청 수 카운터 메트릭명. 기본값 LOAD_05_requests_total
#                           (goods-limited-drop-spike.js:75 requestCounter) — "이 실행이 실제로
#                           트래픽을 냈는가" 무활동 게이트(judge 전 선행 검증)에 사용한다
#   MYSQL_HOST/PORT/USER/PASSWORD/ROOT_PASSWORD/DATABASE/SERVICE_NAME/COMPOSE_PROJECT_NAME
#                           qa/load/provision/provision.sh와 동일 접속 계열(호스트 직접 접속 우선 →
#                           docker compose exec 폴백 → 라벨 기반 컨테이너 탐색 최종 폴백)
#   REDIS_HOST/PORT/SERVICE_NAME
#                           qa/load/reseed/reseed.sh와 동일 접속 계열
#
# 종료 코드:
#   0  모든 판정 통과(누수 등식 성립·응답 수 등식 성립·오버셀 없음)
#   1  하나 이상 실제 실패 판정(누수>0, 응답 불일치(F3, order>accepted) 또는 유령 성공
#      (order<accepted), 오버셀>0) — 측정 불가 판정이 함께 있어도 실패가 우선한다
#   2  측정 불가(예약 마커 수<주문 수, k6 202 카운터·MySQL·Redis 조회 결과가 정수가 아님,
#      **또는 이 실행 자체가 트래픽을 못 냈음**(k6 총 요청 수=0 또는 reserved·order·accepted
#      전부 0 — QA_JWT_SECRET 불일치로 전 요청이 401 처리되는 등, is_execution_measurable
#      게이트) 등 — 누수 0/불일치 0으로 오판하지 않기 위한 별도 코드. DROP_ID 오류·마커 TTL
#      만료·인증 실패·인프라 조회 실패 등 실제 원인 확인 필요. 단 실패 판정이 함께 있으면
#      종료코드는 1)
#
# --self-test: 판정 함수 3종 + 정수 검증 헬퍼 + 무활동 게이트(is_execution_measurable) +
#              종료코드 합성(resolve_overall_exit) + SCAN 커서 무한 루프 회귀를 티켓 "테스트 케이스"와
#              코드 리뷰 재발 방지 케이스(측정 불가 3분기·유령 성공·정수 검증·무활동 오판·커서 파싱)로
#              재현하고 종료한다 (인프라 불필요 — MySQL/Redis/k6 산출물 없이 로직만 검증. 단 SCAN 커서
#              케이스는 mktemp + 백그라운드 프로세스를 사용한다).

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
REQUESTS_METRIC_NAME="${REQUESTS_METRIC_NAME:-LOAD_05_requests_total}"

log() { echo "[verify-limited-drop] $*"; }
warn() { echo "[verify-limited-drop][WARN] $*" >&2; }

# ============================================================
# 판정 함수 (순수 함수 — stdout에 차이값, 종료코드로 통과/실패) — 티켓 "테스트 케이스" 대응
# ============================================================

# "예약 마커 수와 주문 수가 같으면 누수 0건 통과(exit 0)" /
# "예약 마커 수가 주문 수보다 많으면 누수 건수를 리포트(exit 1)" /
# "예약 마커 수가 주문 수보다 적으면 측정 불가로 판정(exit 2)" — 마커 TTL 만료 또는 DROP_ID 오기입을
# 의심해야 하는 상태이며, 이걸 "누수 0건"으로 뭉개면 실제 누수를 놓친 채 통과시키는 false green이 된다.
judge_leak() {
    local reserved_count="$1"
    local order_count="$2"
    local diff=$((reserved_count - order_count))
    if [ "${diff}" -gt 0 ]; then
        echo "${diff}"
        return 1
    fi
    if [ "${diff}" -lt 0 ]; then
        echo "${diff}"
        return 2
    fi
    echo "0"
    return 0
}

# "주문 수가 k6 202 카운트보다 많으면 응답 불일치(F3) 건수를 리포트(exit 1)" /
# "같으면 통과(exit 0)" /
# "주문 수가 202 카운트보다 적으면 유령 성공을 리포트(exit 1)" — 202는 받았는데 DB 주문이 없는
# 상태로, F3(주문은 있는데 202를 못 받음)와 원인이 반대라 호출부가 값의 부호로 구분해 별도 메시지를
# 낸다. ORDER_SINCE_TIMESTAMP 측정 구간 오설정으로도 발생할 수 있다.
judge_response_mismatch() {
    local order_count="$1"
    local accepted_count="$2"
    local diff=$((order_count - accepted_count))
    if [ "${diff}" -eq 0 ]; then
        echo "0"
        return 0
    fi
    echo "${diff}"
    return 1
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

# "측정값이 음이 아닌 정수인지 검증한다" — MySQL/Redis/jq 조회가 실패하면 빈 문자열·에러 텍스트가
# 반환되는데, bash 산술에 그대로 넣으면 0으로 강제 변환되어 인프라 실패가 "누수 0건"·"불일치 0건"
# 같은 false green으로 둔갑한다. main()은 각 측정 직후 이 함수로 걸러 실패 시 exit 2로 보낸다.
is_nonneg_integer() {
    [[ "$1" =~ ^[0-9]+$ ]]
}

# "이 실행이 실제로 트래픽을 냈는가" 무활동 게이트 (신규 회귀 방지, code-review p1) — 1차 수정에서
# 이 게이트를 제거하고 judge_leak(0,0)=통과로만 판정한 탓에, QA_JWT_SECRET 불일치로 전 요청이 401
# 처리되면(goods-limited-drop-spike.js:23-27이 문서화한 실패 모드) reserved=0, order=0, accepted=0이
# 되어 판정 3종이 전부 등식(0=0)을 이뤄 "누수 없음·불일치 없음·오버셀 없음"으로 오판됐다
# (exit 0 — 수정 전 a225d94f는 exit 2였다). 아래 둘 중 하나라도 해당하면 무활동(측정 불가)으로
# 판단해 false(1)를 반환한다. 이미 is_nonneg_integer로 정수 검증된 값을 입력으로 받는다는 전제다.
#   1. k6 총 요청 수(requests_total)가 0 — k6 실행 자체가 트래픽을 못 냈다
#   2. reserved·order·accepted 세 값이 모두 0 — 요청은 나갔어도 관측된 게 아무것도 없다
is_execution_measurable() {
    local requests_total="$1"
    local reserved_count="$2"
    local order_count="$3"
    local accepted_count="$4"
    if [ "${requests_total}" -eq 0 ]; then
        return 1
    fi
    if [ "${reserved_count}" -eq 0 ] && [ "${order_count}" -eq 0 ] && [ "${accepted_count}" -eq 0 ]; then
        return 1
    fi
    return 0
}

# "실패(any_failure)와 측정 불가(any_unmeasurable) 두 플래그로 최종 종료코드를 합성한다" —
# 실패가 있으면 측정 불가 여부와 무관하게 항상 1, 실패 없이 측정 불가만 있으면 2, 둘 다 없으면 0.
# main()의 핵심 신규 분기이자 이번 p1 회귀가 발생한 지점이라 순수 함수로 분리해 self-test로
# 4가지 조합을 전수 검증한다(code-review p2 재발 방지 — 이 로직은 1차 수정에서 self-test 커버가
# 전혀 없었다).
resolve_overall_exit() {
    local any_failure="$1"
    local any_unmeasurable="$2"
    if [ "${any_failure}" -eq 1 ]; then
        echo 1
        return 0
    fi
    if [ "${any_unmeasurable}" -eq 1 ]; then
        echo 2
        return 0
    fi
    echo 0
    return 0
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

    # 2) 예약 마커 수가 주문 수보다 많으면 누수 건수를 차이값으로, 종료코드 1(F1 실패)
    #    (실측-리포트.md L64-69: reserved=2000, goods_orders=1357 → leak=643)
    leak_output="$(judge_leak 2000 1357)"; leak_exit=$?
    if [ "${leak_output}" = "643" ] && [ "${leak_exit}" -eq 1 ]; then
        log "PASS: judge_leak(2000,1357) -> leak=${leak_output} exit=${leak_exit} (기대: 643/1)"
    else
        warn "FAIL: judge_leak(2000,1357) -> leak=${leak_output} exit=${leak_exit} (기대: 643/1)"
        self_test_failures=$((self_test_failures + 1))
    fi

    # 2-1) 예약 마커 수가 주문 수보다 적으면 측정 불가로 판정, 종료코드 2 — 마커 TTL 만료 또는
    #      DROP_ID 오기입 의심 시나리오이며 "누수 0건"으로 오판해선 안 된다 (p1 재발 방지 케이스)
    leak_output="$(judge_leak 1357 2000)"; leak_exit=$?
    if [ "${leak_output}" = "-643" ] && [ "${leak_exit}" -eq 2 ]; then
        log "PASS: judge_leak(1357,2000) -> diff=${leak_output} exit=${leak_exit} (기대: -643/2, 측정 불가)"
    else
        warn "FAIL: judge_leak(1357,2000) -> diff=${leak_output} exit=${leak_exit} (기대: -643/2)"
        self_test_failures=$((self_test_failures + 1))
    fi

    # 2-2) 예약 키가 0건(reserved=0)인데 주문은 있으면(order>0) 위와 동일 원리로 측정 불가 판정.
    #      단 이 판정은 order>0일 때만 성립한다 — reserved도 order도 0인 완전 무활동은 diff=0이
    #      되어 이 함수만으로는 "누수 0건 통과"로 나온다(아래 2-3, is_execution_measurable 참고).
    leak_output="$(judge_leak 0 5)"; leak_exit=$?
    if [ "${leak_output}" = "-5" ] && [ "${leak_exit}" -eq 2 ]; then
        log "PASS: judge_leak(0,5) -> diff=${leak_output} exit=${leak_exit} (기대: -5/2, 예약 키 0건 측정 불가)"
    else
        warn "FAIL: judge_leak(0,5) -> diff=${leak_output} exit=${leak_exit} (기대: -5/2)"
        self_test_failures=$((self_test_failures + 1))
    fi

    # 2-3) reserved=0, order=0(완전 무활동 — 예: QA_JWT_SECRET 불일치로 전 요청 401)이면
    #      judge_leak 단독으로는 diff=0/exit=0(누수 없음 "통과")으로 나온다 — 이것이 정확히
    #      code-review에서 지적된 신규 회귀(exit 0 오판)의 뿌리이며, judge_leak 만으로는 무활동을
    #      구분할 수 없다는 사실 자체를 문서화하는 케이스다. 실제 무활동 차단은 main()이 판정
    #      전에 호출하는 is_execution_measurable 게이트가 담당한다(아래 7번 케이스).
    leak_output="$(judge_leak 0 0)"; leak_exit=$?
    if [ "${leak_output}" = "0" ] && [ "${leak_exit}" -eq 0 ]; then
        log "PASS: judge_leak(0,0) -> leak=${leak_output} exit=${leak_exit} (기대: 0/0, 함수 단독으로는 무활동 구분 불가 — 게이트는 is_execution_measurable)"
    else
        warn "FAIL: judge_leak(0,0) -> leak=${leak_output} exit=${leak_exit} (기대: 0/0)"
        self_test_failures=$((self_test_failures + 1))
    fi

    # 3) 주문 수가 k6 202 카운트보다 많으면 응답 불일치(F3) 건수를 리포트, 종료코드 1
    #    (실측-리포트.md L92: goods_orders=1357, 202=228 → mismatch=1129)
    local mismatch_output mismatch_exit
    mismatch_output="$(judge_response_mismatch 1357 228)"; mismatch_exit=$?
    if [ "${mismatch_output}" = "1129" ] && [ "${mismatch_exit}" -eq 1 ]; then
        log "PASS: judge_response_mismatch(1357,228) -> mismatch=${mismatch_output} exit=${mismatch_exit} (기대: 1129/1)"
    else
        warn "FAIL: judge_response_mismatch(1357,228) -> mismatch=${mismatch_output} exit=${mismatch_exit} (기대: 1129/1)"
        self_test_failures=$((self_test_failures + 1))
    fi

    # 3-1) 주문 수와 202 카운트가 같으면 응답 불일치 없음(통과), 종료코드 0
    mismatch_output="$(judge_response_mismatch 228 228)"; mismatch_exit=$?
    if [ "${mismatch_output}" = "0" ] && [ "${mismatch_exit}" -eq 0 ]; then
        log "PASS: judge_response_mismatch(228,228) -> mismatch=${mismatch_output} exit=${mismatch_exit} (기대: 0/0)"
    else
        warn "FAIL: judge_response_mismatch(228,228) -> mismatch=${mismatch_output} exit=${mismatch_exit} (기대: 0/0)"
        self_test_failures=$((self_test_failures + 1))
    fi

    # 3-2) 주문 수가 202 카운트보다 적으면 유령 성공(202는 받았는데 DB 주문이 없다) — F3와 원인이
    #      반대인 별도 실패로, 종료코드는 똑같이 1이지만 값의 부호로 구분한다 (p1 재발 방지 케이스)
    mismatch_output="$(judge_response_mismatch 200 228)"; mismatch_exit=$?
    if [ "${mismatch_output}" = "-28" ] && [ "${mismatch_exit}" -eq 1 ]; then
        log "PASS: judge_response_mismatch(200,228) -> mismatch=${mismatch_output} exit=${mismatch_exit} (기대: -28/1, 유령 성공)"
    else
        warn "FAIL: judge_response_mismatch(200,228) -> mismatch=${mismatch_output} exit=${mismatch_exit} (기대: -28/1)"
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

    # 5) 측정값 정수 검증 — 빈 문자열(인프라 조회 실패)은 거부한다 (p2 재발 방지 케이스)
    if is_nonneg_integer ""; then
        warn "FAIL: is_nonneg_integer('') -> true (기대: false, 빈 문자열은 거부)"
        self_test_failures=$((self_test_failures + 1))
    else
        log "PASS: is_nonneg_integer('') -> false (빈 문자열 거부 — 인프라 조회 실패를 오진하지 않는다)"
    fi

    # 5-1) 음이 아닌 정수는 통과
    if is_nonneg_integer "2000"; then
        log "PASS: is_nonneg_integer('2000') -> true"
    else
        warn "FAIL: is_nonneg_integer('2000') -> false (기대: true)"
        self_test_failures=$((self_test_failures + 1))
    fi

    # 5-2) 음수·비정수 텍스트는 거부
    if is_nonneg_integer "-5"; then
        warn "FAIL: is_nonneg_integer('-5') -> true (기대: false, 음수 거부)"
        self_test_failures=$((self_test_failures + 1))
    else
        log "PASS: is_nonneg_integer('-5') -> false (음수 거부)"
    fi
    if is_nonneg_integer "ERROR 1045"; then
        warn "FAIL: is_nonneg_integer('ERROR 1045') -> true (기대: false, MySQL 에러 텍스트 거부)"
        self_test_failures=$((self_test_failures + 1))
    else
        log "PASS: is_nonneg_integer('ERROR 1045') -> false (에러 텍스트 거부)"
    fi

    # 6) 예약 키 열거에 KEYS를 사용하지 않는다(SCAN만) — 이 스크립트 자신의 소스를 grep으로 검증
    if grep -nE '(^|[^A-Za-z_])redis_exec[[:space:]]+KEYS([^A-Za-z_]|$)' "${BASH_SOURCE[0]}" >/dev/null 2>&1; then
        warn "FAIL: 이 스크립트가 Redis KEYS 명령을 사용합니다(SCAN만 허용)"
        self_test_failures=$((self_test_failures + 1))
    else
        log "PASS: Redis KEYS 명령 미사용 확인(SCAN 기반 count_keys_matching만 사용)"
    fi

    # 7) 무활동 게이트(is_execution_measurable) — 신규 회귀(p1) 재발 방지 케이스.
    #    티켓 재현표: reserved=0/order=0/accepted=0/limitedQuantity=2000 실행에서 수정 전(a225d94f)은
    #    exit 2였는데, 1차 수정에서 exit 0(전부 통과)으로 퇴행했다. 아래 세 조합으로 게이트를 검증한다.
    if is_execution_measurable 0 0 0 0; then
        warn "FAIL: is_execution_measurable(requests=0,reserved=0,order=0,accepted=0) -> true (기대: false, k6 총 요청 0)"
        self_test_failures=$((self_test_failures + 1))
    else
        log "PASS: is_execution_measurable(requests=0,reserved=0,order=0,accepted=0) -> false (k6 총 요청 수 0 — 무활동)"
    fi

    # 7-1) k6 총 요청 수는 있지만(요청 자체는 나갔지만) reserved·order·accepted가 전부 0인 경우 —
    #      QA_JWT_SECRET 불일치로 전 요청이 401 처리된 티켓 재현표 시나리오와 동일 신호
    if is_execution_measurable 500 0 0 0; then
        warn "FAIL: is_execution_measurable(requests=500,reserved=0,order=0,accepted=0) -> true (기대: false, 관측값 전부 0)"
        self_test_failures=$((self_test_failures + 1))
    else
        log "PASS: is_execution_measurable(requests=500,reserved=0,order=0,accepted=0) -> false (관측값 전부 0 — 무활동)"
    fi

    # 7-2) 정상 실행(요청·예약·주문·응답 모두 관측됨)이면 게이트 통과
    if is_execution_measurable 1500 2000 1357 228; then
        log "PASS: is_execution_measurable(requests=1500,reserved=2000,order=1357,accepted=228) -> true (정상 실행)"
    else
        warn "FAIL: is_execution_measurable(requests=1500,reserved=2000,order=1357,accepted=228) -> false (기대: true)"
        self_test_failures=$((self_test_failures + 1))
    fi

    # 8) 종료코드 합성(resolve_overall_exit) — 실패·측정 불가 플래그 4조합 전수 (p2 재발 방지,
    #    이 로직이 정확히 p1 회귀가 발생한 지점이다)
    local resolve_exit_output
    resolve_exit_output="$(resolve_overall_exit 0 0)"
    if [ "${resolve_exit_output}" = "0" ]; then
        log "PASS: resolve_overall_exit(any_failure=0,any_unmeasurable=0) -> ${resolve_exit_output} (기대: 0)"
    else
        warn "FAIL: resolve_overall_exit(0,0) -> ${resolve_exit_output} (기대: 0)"
        self_test_failures=$((self_test_failures + 1))
    fi
    resolve_exit_output="$(resolve_overall_exit 1 0)"
    if [ "${resolve_exit_output}" = "1" ]; then
        log "PASS: resolve_overall_exit(any_failure=1,any_unmeasurable=0) -> ${resolve_exit_output} (기대: 1)"
    else
        warn "FAIL: resolve_overall_exit(1,0) -> ${resolve_exit_output} (기대: 1)"
        self_test_failures=$((self_test_failures + 1))
    fi
    resolve_exit_output="$(resolve_overall_exit 0 1)"
    if [ "${resolve_exit_output}" = "2" ]; then
        log "PASS: resolve_overall_exit(any_failure=0,any_unmeasurable=1) -> ${resolve_exit_output} (기대: 2)"
    else
        warn "FAIL: resolve_overall_exit(0,1) -> ${resolve_exit_output} (기대: 2)"
        self_test_failures=$((self_test_failures + 1))
    fi
    resolve_exit_output="$(resolve_overall_exit 1 1)"
    if [ "${resolve_exit_output}" = "1" ]; then
        log "PASS: resolve_overall_exit(any_failure=1,any_unmeasurable=1) -> ${resolve_exit_output} (기대: 1, 실패 우선)"
    else
        warn "FAIL: resolve_overall_exit(1,1) -> ${resolve_exit_output} (기대: 1)"
        self_test_failures=$((self_test_failures + 1))
    fi

    # 9) SCAN 커서 무한 루프 회귀 재현 — redis-cli 에러 텍스트(NOAUTH/DENIED 등)가 커서 자리에 그대로
    #    반환되면(REDIS_HOST 미설정 + requirepass/protected-mode 환경) 숫자 비교가 영원히 거짓이 되어
    #    무한 대기에 빠지는 회귀가 있었다(code-review p2). redis_exec를 이 케이스 한정으로 오버라이드해
    #    재현하고, GNU timeout 커맨드에 의존하지 않는 순수 bash 워치독(백그라운드 kill)으로 유한 시간
    #    내 종료·비정상 반환을 검증한다.
    local original_redis_exec_definition
    original_redis_exec_definition="$(declare -f redis_exec)"
    # shellcheck disable=SC2317  # self-test 한정 오버라이드 — 아래에서 즉시 호출된다
    redis_exec() { printf 'NOAUTH Authentication required.\n'; }

    local scan_hang_capture_file
    scan_hang_capture_file="$(mktemp)"
    count_keys_matching "goods:limited-drop:1:reserved:*" > "${scan_hang_capture_file}" 2>/dev/null &
    local scan_hang_pid=$!
    local scan_hang_waited_ticks=0
    local scan_hang_timed_out=0
    while kill -0 "${scan_hang_pid}" 2>/dev/null; do
        sleep 0.2
        scan_hang_waited_ticks=$((scan_hang_waited_ticks + 1))
        if [ "${scan_hang_waited_ticks}" -gt 25 ]; then # 5초(0.2s x 25) 상한
            kill -9 "${scan_hang_pid}" 2>/dev/null
            scan_hang_timed_out=1
            break
        fi
    done
    wait "${scan_hang_pid}" 2>/dev/null

    eval "${original_redis_exec_definition}" # redis_exec 원복

    local scan_hang_output
    scan_hang_output="$(cat "${scan_hang_capture_file}" 2>/dev/null || true)"
    rm -f "${scan_hang_capture_file}"

    if [ "${scan_hang_timed_out}" -eq 1 ]; then
        warn "FAIL: count_keys_matching이 에러 텍스트 커서 입력에 5초 내 종료되지 않았습니다(무한 루프 회귀)"
        self_test_failures=$((self_test_failures + 1))
    elif [ -z "${scan_hang_output}" ]; then
        log "PASS: count_keys_matching이 에러 텍스트 커서를 무한 루프 없이 즉시 거부(빈 값 반환, 대기 ${scan_hang_waited_ticks}x0.2s)"
    else
        warn "FAIL: count_keys_matching이 에러 텍스트 커서에서 예상 밖 출력을 반환했습니다(값='${scan_hang_output}')"
        self_test_failures=$((self_test_failures + 1))
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
    # stdout을 변수로 캡처만 하고, 성공했을 때만 출력한다 — docker compose exec가 stdout 일부를
    # 쓴 뒤 비-0으로 종료하면(연결 끊김 등) 그대로 흘려보내던 예전 방식은 아래 컨테이너 직접 폴백이
    # 같은 SCAN 결과를 다시 방출해 호출부(count_keys_matching)의 줄 수가 중복 집계될 수 있었다.
    local compose_output
    if compose_output="$(cd "${REPOSITORY_ROOT}" && docker compose "${compose_project_flag[@]+"${compose_project_flag[@]}"}" exec -T "${REDIS_SERVICE_NAME}" \
            redis-cli "$@" 2>/dev/null)"; then
        printf '%s\n' "${compose_output}"
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
# SCAN은 at-least-once만 보장한다 — 커서 순회 도중 슬롯 리해시가 일어나면 같은 키가 두 번 이상
# 반환될 수 있다(Redis 공식 SCAN guarantees). 커서 페이지마다 줄 수를 그대로 누적하면 실제 키 수보다
# 부풀려질 수 있으므로, 전체 매칭 키를 모아 정렬 후 중복 제거(sort -u)한 뒤에 센다.
#
# 실패(접속 불가·커서 파싱 불가)는 "0"이 아니라 빈 문자열을 stdout에 남기고 비정상 종료한다(exit 1) —
# 호출부(measure_reserved_count → main의 is_nonneg_integer 검증)가 인프라 실패와 "실제 키 0건"을
# 구분하게 하기 위함이다(code-review p2). 또한 REDIS_HOST 미설정 + requirepass/protected-mode
# 환경에서는 redis-cli가 NOAUTH/DENIED 같은 에러 텍스트를 stdout 첫 줄(커서 자리)에 그대로 반환하는데,
# 예전 구현은 이를 숫자로 취급하지 않고도 cursor="0"이 될 때까지 무한 대기했다(신규 회귀, code-review
# p2) — 매 반복 커서를 is_nonneg_integer로 검증해 실패 시 즉시 반환하고, 반복 횟수 상한도 둔다.
count_keys_matching() {
    local pattern="$1"
    local cursor="0"
    local all_matched_keys=""
    local scan_iterations=0
    local max_scan_iterations=10000
    while :; do
        scan_iterations=$((scan_iterations + 1))
        if [ "${scan_iterations}" -gt "${max_scan_iterations}" ]; then
            warn "SCAN 반복 상한(${max_scan_iterations}) 초과 — 커서 순회가 종료되지 않습니다(pattern='${pattern}')"
            return 1
        fi

        local scan_result
        # 비-tty 파이프 실행 시 redis-cli는 raw 모드로 "커서\n키1\n키2..." 평평한 줄 단위 출력을 낸다.
        scan_result="$(redis_exec SCAN "${cursor}" MATCH "${pattern}" COUNT 200 2>/dev/null || true)"
        if [ -z "${scan_result}" ]; then
            # 접속 실패 또는 SCAN 미응답 — "0"으로 뭉개면 인프라 실패가 "키 0건"으로 둔갑한다(p2)
            return 1
        fi

        local next_cursor
        next_cursor="$(echo "${scan_result}" | sed -n '1p' | tr -d '"\r')"
        if ! is_nonneg_integer "${next_cursor}"; then
            # redis-cli 에러 텍스트(NOAUTH/DENIED 등)가 커서 자리에 반환된 경우 — 무한 루프 대신 즉시 실패
            warn "SCAN 커서가 정수가 아닙니다(값='${next_cursor}') — Redis 인증·접속 실패를 의심하세요"
            return 1
        fi
        cursor="${next_cursor}"

        local matched_keys
        matched_keys="$(echo "${scan_result}" | tail -n +2 | tr -d '"')"
        if [ -n "${matched_keys}" ]; then
            all_matched_keys="${all_matched_keys}
${matched_keys}"
        fi
        if [ "${cursor}" = "0" ]; then
            break
        fi
    done
    if [ -z "${all_matched_keys}" ]; then
        echo "0"
        return 0
    fi
    echo "${all_matched_keys}" | sort -u | grep -c .
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
#   상태(PENDING 등) 무관하게 전체를 센다 — 설계 §8-2 문구는 "PENDING 수"지만, 이 하네스의 목적은
#   "이번 실행이 생성한 주문 수"이므로 이후 CONFIRMED 등으로 전이된 주문도 포함하는 것이 의도적
#   이탈이다(계측 시점에 따라 일부가 이미 상태 전이됐다고 누수·오버셀 판정에서 빠지면 안 된다).
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
# 메트릭 부재(오타 등)와 실제 count=0을 구분한다 — `// 0` 기본값만 쓰면 오타로 인한 부재가
# 실제 0건으로 둔갑해 is_nonneg_integer 검증을 무의미하게 만들고, 엉뚱한 결함("F3 응답 불일치")으로
# 오보고된다(code-review p2). has() 존재 검사를 먼저 거친다.
measure_accepted_count() {
    if ! command -v jq >/dev/null 2>&1; then
        warn "jq가 설치돼 있지 않습니다 — k6 summary JSON 파싱 불가"
        return 1
    fi
    if [ ! -f "${K6_SUMMARY_JSON}" ]; then
        warn "K6_SUMMARY_JSON 파일을 찾을 수 없습니다: ${K6_SUMMARY_JSON}"
        return 1
    fi
    if ! jq -e --arg name "${ACCEPTED_METRIC_NAME}" '.metrics | has($name)' "${K6_SUMMARY_JSON}" >/dev/null 2>&1; then
        warn "측정 불가: k6 summary JSON에 메트릭 '${ACCEPTED_METRIC_NAME}'이 없습니다 — ACCEPTED_METRIC_NAME 오타를 의심하세요"
        return 1
    fi
    jq -r --arg name "${ACCEPTED_METRIC_NAME}" '.metrics[$name].count // 0' "${K6_SUMMARY_JSON}"
}

# ============================================================
# 실측정: k6 총 요청 수 — 무활동 게이트(is_execution_measurable)의 입력. 마찬가지로 메트릭 부재와
# 실제 count=0을 구분한다.
# ============================================================
measure_requests_total() {
    if ! command -v jq >/dev/null 2>&1; then
        warn "jq가 설치돼 있지 않습니다 — k6 summary JSON 파싱 불가"
        return 1
    fi
    if [ ! -f "${K6_SUMMARY_JSON}" ]; then
        warn "K6_SUMMARY_JSON 파일을 찾을 수 없습니다: ${K6_SUMMARY_JSON}"
        return 1
    fi
    if ! jq -e --arg name "${REQUESTS_METRIC_NAME}" '.metrics | has($name)' "${K6_SUMMARY_JSON}" >/dev/null 2>&1; then
        warn "측정 불가: k6 summary JSON에 메트릭 '${REQUESTS_METRIC_NAME}'이 없습니다 — REQUESTS_METRIC_NAME 오타를 의심하세요"
        return 1
    fi
    jq -r --arg name "${REQUESTS_METRIC_NAME}" '.metrics[$name].count // 0' "${K6_SUMMARY_JSON}"
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

    # 각 측정 직후 정수 검증(is_nonneg_integer)을 거친다 — MySQL/Redis/jq 조회 실패로 빈 문자열·
    # 에러 텍스트가 반환되면 이후 산술이 0으로 강제 변환되어 인프라 실패를 "누수 0건"·"응답 불일치
    # 0건" 같은 false green으로 오판할 수 있기 때문이다(p2).
    local requests_total reserved_count remaining_value order_count accepted_count
    if ! requests_total="$(measure_requests_total)"; then
        warn "k6 총 요청 수(${REQUESTS_METRIC_NAME}) 카운터를 읽지 못했습니다 — 검증 불가"
        exit 2
    fi
    if ! is_nonneg_integer "${requests_total}"; then
        warn "측정 불가: k6 총 요청 수(summary JSON) 값이 정수가 아닙니다(값='${requests_total}') — " \
             "REQUESTS_METRIC_NAME='${REQUESTS_METRIC_NAME}' 오타를 의심하세요"
        exit 2
    fi

    reserved_count="$(measure_reserved_count)"
    if ! is_nonneg_integer "${reserved_count}"; then
        warn "측정 불가: 예약 마커 수(Redis SCAN) 결과가 정수가 아닙니다(값='${reserved_count}') — " \
             "Redis 접속 실패를 의심하세요"
        exit 2
    fi

    remaining_value="$(measure_remaining)"

    order_count="$(measure_order_count "${mysql_target}")"
    if ! is_nonneg_integer "${order_count}"; then
        warn "측정 불가: DB 주문 수(MySQL 조회) 결과가 정수가 아닙니다(값='${order_count}') — " \
             "MySQL 접속·쿼리 실패를 의심하세요"
        exit 2
    fi

    if ! accepted_count="$(measure_accepted_count)"; then
        warn "k6 202 카운터를 읽지 못했습니다 — 검증 불가"
        exit 2
    fi
    if ! is_nonneg_integer "${accepted_count}"; then
        warn "측정 불가: k6 202 카운터(summary JSON) 값이 정수가 아닙니다(값='${accepted_count}') — " \
             "ACCEPTED_METRIC_NAME='${ACCEPTED_METRIC_NAME}' 오타를 의심하세요"
        exit 2
    fi

    log "관측값: requests(k6)=${requests_total} reserved(Redis)=${reserved_count} remaining(Redis)=${remaining_value} order(DB)=${order_count} accepted(k6 202)=${accepted_count}"

    # 무활동 게이트(신규 회귀 방지, code-review p1) — k6 총 요청 수가 0이거나 reserved·order·
    # accepted가 전부 0이면(예: QA_JWT_SECRET 불일치로 전 요청 401) 판정 3종이 전부 등식(0=0)을
    # 이뤄 "통과"로 오판된다. 판정 실행 전에 무활동 실행을 걸러 exit 2로 보낸다.
    if ! is_execution_measurable "${requests_total}" "${reserved_count}" "${order_count}" "${accepted_count}"; then
        warn "측정 불가: 이 실행이 트래픽을 내지 못했습니다(requests=${requests_total}, reserved=${reserved_count}, " \
             "order=${order_count}, accepted=${accepted_count}) — QA_JWT_SECRET 불일치로 전 요청이 401 처리됐을 " \
             "가능성을 의심하세요(goods-limited-drop-spike.js:23-27 참고). 판정 3종을 등식 통과(0=0)로 " \
             "오판하지 않기 위해 실행 자체를 측정 불가로 차단합니다"
        exit 2
    fi

    # 실패(exit 1)와 측정 불가(exit 2)를 분리 추적한다 — 둘 다 발생하면 실제 실패가 우선이다
    # (측정 불가만 있고 실패가 없을 때만 최종 종료코드 2를 낸다).
    local any_failure=0
    local any_unmeasurable=0

    local leak_output leak_exit
    leak_output="$(judge_leak "${reserved_count}" "${order_count}")"; leak_exit=$?
    case "${leak_exit}" in
        0)
            log "판정 1/3 누수(F1): 0건 통과 (reserved=${reserved_count}, order=${order_count})"
            ;;
        1)
            warn "판정 1/3 누수(F1): ${leak_output}건 실패 (reserved=${reserved_count} - order=${order_count})"
            any_failure=1
            ;;
        *)
            warn "판정 1/3 누수(F1): 측정 불가 — reserved(${reserved_count}) < order(${order_count}), 차이=${leak_output}. " \
                 "예약 마커 TTL 만료(app.limited-drop.reservation.marker-ttl-seconds, 기본 600초 — " \
                 "confirmSuccess가 no-op이라 성공 건 마커도 남아 시간 경과 시 만료됨) 또는 DROP_ID 오기입을 의심하세요"
            any_unmeasurable=1
            ;;
    esac

    local mismatch_output mismatch_exit
    mismatch_output="$(judge_response_mismatch "${order_count}" "${accepted_count}")"; mismatch_exit=$?
    if [ "${mismatch_exit}" -eq 0 ]; then
        log "판정 2/3 응답 불일치(F3): 0건 통과 (order=${order_count}, accepted=${accepted_count})"
    elif [ "${mismatch_output}" -gt 0 ]; then
        warn "판정 2/3 응답 불일치(F3): ${mismatch_output}건 실패 (order=${order_count} - accepted=${accepted_count})"
        any_failure=1
    else
        warn "판정 2/3 유령 성공: ${mismatch_output#-}건 실패 — 202는 받았는데 DB 주문이 없습니다 " \
             "(order=${order_count}, accepted=${accepted_count}). ORDER_SINCE_TIMESTAMP 측정 구간 오설정, 또는 " \
             "measure_order_count 쿼리의 o.deleted_at IS NULL 조건으로 인해 검증 시점까지 취소·소프트 삭제된 " \
             "주문이 카운트에서 빠졌을 가능성도 의심하세요"
        any_failure=1
    fi

    local oversell_output oversell_exit
    oversell_output="$(judge_oversell "${order_count}" "${LIMITED_QUANTITY}")"; oversell_exit=$?
    if [ "${oversell_exit}" -eq 0 ]; then
        log "판정 3/3 오버셀(회귀 보호): 없음 통과 (order=${order_count}, limitedQuantity=${LIMITED_QUANTITY})"
    else
        warn "판정 3/3 오버셀(회귀 보호): ${oversell_output}건 실패 — 게이트 회귀! (order=${order_count} - limitedQuantity=${LIMITED_QUANTITY})"
        any_failure=1
    fi

    local overall_exit
    overall_exit="$(resolve_overall_exit "${any_failure}" "${any_unmeasurable}")"

    log "=== 검증 종료 (종료코드 ${overall_exit}) ==="
    exit "${overall_exit}"
}

if [ "${1:-}" = "--self-test" ]; then
    run_self_test
    exit $?
fi

main "$@"
