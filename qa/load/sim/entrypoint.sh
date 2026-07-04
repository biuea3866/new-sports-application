#!/usr/bin/env bash
# qa/load/sim/entrypoint.sh — k6-runner 컨테이너 진입점 (⑦ 상시 트래픽 시뮬레이터, INFRA-09)
#
# 근거 티켓: 프로젝트/스포츠앱/상시 트래픽 시뮬레이터/Tickets/INFRA-09-compose-sim-제어-대시보드-통합.md
# 근거 TDD: ../../TDD.md "서버 토폴로지"·"상태 전이 표"(FR-7)·"Sequence Diagram".
#
# 역할: INFRA-04~07이 각자 독립 파일로 작성한 4개 k6 시나리오
#   (b2c-diurnal-read.js·b2c-diurnal-write.js·b2b-diurnal.js: 상시 배경 곡선,
#    marketing-spike.js: 예약 스파이크)를 **하나의 k6-runner 컨테이너 안에서 병렬 프로세스**로
#   기동한다. 각 스크립트는 자기 자신의 options.scenarios를 갖는 독립 k6 테스트 파일이라
#   (SCENARIO_ID/executor가 서로 다름) 단일 k6 실행으로 합치지 않고, 별도 `k6 run` 프로세스
#   4개를 이 스크립트가 백그라운드로 띄우고 종료를 조율한다(스크립트 파일 자체는 이 티켓의
#   소유 범위 밖 — INFRA-04~07이 이미 커밋한 그대로 무수정 참조만 한다).
#
# gap report 경로 계약(qa/load/k6/lib/gapreport.js#handleSummary)이 "qa/load/results/<id>-gap.json"
# 상대경로를 반환하므로, 이 스크립트는 반드시 cwd="/"(즉 qa/load 마운트의 부모 디렉터리)에서
# k6를 실행해야 그 상대경로가 컨테이너의 /qa/load/results/ 에 그대로 떨어진다
# (docker-compose.sim.yml의 k6-runner working_dir: / 와 짝을 이루는 계약).
#
# 종료 처리(FR-7 "graceful ramp-to-0"): SIGTERM을 받으면 각 k6 하위 프로세스에 SIGTERM을
# 전달한다. k6는 SIGTERM/SIGINT 수신 시 각 시나리오의 gracefulStop(기본 30s, k6 기본값)
# 동안 도착률을 0으로 수렴시키며 진행 중인 iteration을 마무리한 뒤 종료한다 — 즉시 kill이
# 아니다. compose 쪽 stop_grace_period(docker-compose.sim.yml, 45s)가 이 gracefulStop보다
# 길게 설정되어 있어야 SIGKILL로 끊기지 않는다.
#
# 마케팅 스파이크 스케줄: QA_SPIKE_START_TIME(예: "3h", "0s")을 marketing-spike.js에
# 그대로 전달한다(그 스크립트의 startTime 스케줄, TDD "예약 트리거"). 기본값은 QA_TIME_SCALE로
# 압축한 하루 안에서 임의 오프셋 없이 "즉시"(0s) 실행 — 실제 상시 운영(TIME_SCALE=1)에서는
# 운영자가 QA_SPIKE_START_TIME을 원하는 하루 중 시각 오프셋으로 override한다.
# QA_ENABLE_MARKETING_SPIKE=false 로 스파이크만 끌 수 있다(배경 곡선 3종은 항상 실행).

set -u
set -o pipefail

K6_SCRIPT_DIR="/qa/load/k6"
RESULTS_DIR="/qa/load/results"
mkdir -p "${RESULTS_DIR}"

QA_ENABLE_MARKETING_SPIKE="${QA_ENABLE_MARKETING_SPIKE:-true}"

log() {
  echo "[k6-runner] $*"
}

declare -a CHILD_PIDS=()
declare -a CHILD_NAMES=()

# 인자: $1 = k6 스크립트 파일명(qa/load/k6/ 기준), $2 = 상태 태깅용 testid 라벨
run_scenario() {
  local script_file="$1"
  local testid="$2"
  local script_path="${K6_SCRIPT_DIR}/${script_file}"

  if [ ! -f "${script_path}" ]; then
    log "[WARN] ${script_path} 가 없습니다 — 스킵 (선행 티켓 미머지 여부 확인)"
    return 1
  fi

  log "시나리오 시작: ${testid} (${script_file})"
  k6 run \
    --tag "testid=${testid}" \
    --out experimental-prometheus-rw \
    "${script_path}" \
    > "${RESULTS_DIR}/${testid}.log" 2>&1 &

  local pid=$!
  CHILD_PIDS+=("${pid}")
  CHILD_NAMES+=("${testid}")
  log "시나리오 PID 할당: ${testid}=${pid}"
}

# ---- 배경 일주기 곡선 3종 (항상 동시 실행 — B2C read/write + B2B) ----
run_scenario "b2c-diurnal-read.js" "b2c-read"
run_scenario "b2c-diurnal-write.js" "b2c-write"
run_scenario "b2b-diurnal.js" "b2b"

# ---- 마케팅 예약 스파이크 (조건부) ----
if [ "${QA_ENABLE_MARKETING_SPIKE}" = "true" ]; then
  run_scenario "marketing-spike.js" "marketing-spike"
else
  log "QA_ENABLE_MARKETING_SPIKE=false — 마케팅 스파이크 미기동"
fi

if [ "${#CHILD_PIDS[@]}" -eq 0 ]; then
  log "[FATAL] 기동된 시나리오가 하나도 없습니다 — 종료"
  exit 1
fi

# shellcheck disable=SC2329 # trap으로 간접 호출됨(아래 `trap terminate_all SIGTERM SIGINT`)
terminate_all() {
  log "종료 신호 수신 — 각 k6 프로세스에 SIGTERM 전달(gracefulStop 도착률 0 수렴 대기)"
  local index
  for index in "${!CHILD_PIDS[@]}"; do
    local pid="${CHILD_PIDS[$index]}"
    if kill -0 "${pid}" 2>/dev/null; then
      log "SIGTERM -> ${CHILD_NAMES[$index]}(pid=${pid})"
      kill -TERM "${pid}" 2>/dev/null || true
    fi
  done
  wait
  log "모든 k6 프로세스 종료 완료"
  exit 0
}

trap terminate_all SIGTERM SIGINT

log "모든 시나리오 기동 완료 — 대기 중 (PID: ${CHILD_PIDS[*]})"
wait
EXIT_CODE=$?
log "모든 k6 프로세스가 자체 종료했습니다(exit=${EXIT_CODE})"
exit "${EXIT_CODE}"
