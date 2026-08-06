#!/usr/bin/env bash
# 머지 게이트 실행기 (R-21·R-24 대응).
#
# 왜 필요한가 — 2026-08-01~03 사이 머지된 PR 7건이 각각 main 에 red 를 남겼다.
# 원인은 게이트가 아래 3종을 **강제 실행하지 않은 것**이다:
#   ① 정적분석: Gradle UP-TO-DATE 캐시가 detekt 실행 자체를 건너뛰어 위반 4건이 연속 통과 (R-21)
#   ② 아키텍처 규칙: 모듈 단위 테스트로는 통과하는 레이어·모듈 그래프 위반
#   ③ 컨텍스트 로드: 프로파일 게이트 빈 주입이 test-jpa 풀부팅 73클래스를 붕괴 (R-24).
#      슬라이스·MockK·모듈 단위 테스트는 이 경로를 부팅하지 않아 전부 통과한다.
#
# 이 게이트는 **전체 테스트 스위트의 대체물이 아니다.** 변경 모듈 테스트(글로벌 push 훅이 요구)
# 위에 얹는 "저비용 고신호 바닥선"이다 — 위 3종은 어떤 변경이든 깨질 수 있고, 깨지면 main 이 red 가 된다.
#
# 산출물: backend/build/merge-gate/report.json (지문·verdict·단계별 exit code)
#   → .claude/hooks/merge-gate-verify.sh 가 push·머지 시 이 리포트를 검증한다.
#     "실행했다"는 단언이 아니라 리포트가 통과 근거다 (COMPLETION-RULE §2 검증 아티팩트).
#
# 사용:
#   scripts/ops/merge-gate.sh                 # 전 tier (기본)
#   scripts/ops/merge-gate.sh --tier=1        # 정적분석만 (자기검증용)
#   scripts/ops/merge-gate.sh --print-fingerprint
set -uo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly BACKEND="$REPO_ROOT/backend"
readonly REPORT_DIR="$BACKEND/build/merge-gate"
readonly REPORT="$REPORT_DIR/report.json"
readonly LOG_DIR="$REPORT_DIR/logs"

source "$REPO_ROOT/scripts/ops/lib/gate-fingerprint.sh"

# tier 3 대표 풀부팅 — 두 프로파일의 컨텍스트 로드를 각각 확인한다.
#   ApplicationContextLoadGateTest : test-jpa 프로파일 (R-24 가 깨뜨린 경로)
#   HealthEndpointIntegrationTest  : 기본 프로파일 (운영 컨텍스트에 가까운 경로)
readonly FULLBOOT_TESTS=(
  "com.sportsapp.health.ApplicationContextLoadGateTest"
  "com.sportsapp.health.HealthEndpointIntegrationTest"
)

TIERS="1,2,3"
for arg in "$@"; do
  case "$arg" in
    --print-fingerprint) gate_fingerprint "$REPO_ROOT"; exit 0 ;;
    --tier=*) TIERS="${arg#--tier=}" ;;
    -h|--help) sed -n '2,30p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) printf '알 수 없는 인수: %s\n' "$arg" >&2; exit 64 ;;
  esac
done

wants_tier() { [[ ",$TIERS," == *",$1,"* ]]; }

mkdir -p "$LOG_DIR"
declare -a STEP_IDS=() STEP_CMDS=() STEP_EXITS=() STEP_SECS=()
VERDICT=PASS

# run_step <id> <설명> <gradle 인수...>
run_step() {
  local id="$1" label="$2"; shift 2
  local log_file="$LOG_DIR/$id.log"
  local started ended rc=0
  started=$(date +%s)
  printf '\n──── [%s] %s\n' "$id" "$label"
  printf '     ./gradlew %s\n' "$*"
  ( cd "$BACKEND" && ./gradlew "$@" ) >"$log_file" 2>&1 || rc=$?
  ended=$(date +%s)

  STEP_IDS+=("$id"); STEP_CMDS+=("./gradlew $*")
  STEP_EXITS+=("$rc"); STEP_SECS+=("$((ended - started))")

  if (( rc == 0 )); then
    printf '     ✅ PASS (%ds)\n' "$((ended - started))"
  else
    VERDICT=FAIL
    printf '     ❌ FAIL exit=%d (%ds) — 로그: %s\n' "$rc" "$((ended - started))" "$log_file"
    # 실패 원인을 바로 보여준다. 파이프로 exit code 를 가리지 않도록 로그 파일에서만 읽는다.
    grep -E "^(e: |> Task .* FAILED|Harness rules 위반|.*detekt.*(issues|위반)|Architecture Violation|[0-9]+ tests? completed)" \
      "$log_file" | head -25 || true
  fi
  return 0
}

printf '머지 게이트 — tier=%s\n' "$TIERS"
printf 'repo: %s\nHEAD: %s\n' "$REPO_ROOT" "$(git -C "$REPO_ROOT" rev-parse --short HEAD)"

# tier 1 — 정적분석. `--rerun-tasks` 로 캐시를 무효화한다.
# UP-TO-DATE 캐시 히트는 위반을 그대로 통과시킨다(R-21 의 4연속 통과가 그 증거).
# 게이트에서는 실행 시간보다 "실제로 스캔했다"가 우선이다.
if wants_tier 1; then
  run_step static "정적분석 8모듈 전수 (detekt + harnessCheck, 캐시 무효화)" \
    detekt harnessCheck --rerun-tasks
fi

# tier 2 — 아키텍처 규칙. bootstrap 의 ArchUnit·그래프 파싱 테스트 전수 (컨테이너 불필요).
if wants_tier 2; then
  run_step arch "아키텍처 규칙 (bootstrap architecture.*)" \
    :bootstrap:test --tests 'com.sportsapp.architecture.*' --rerun
fi

# tier 3 — 컨텍스트 로드. R-24 유형은 이 단계만 드러낸다.
if wants_tier 3; then
  tier3_args=(:bootstrap:test)
  for t in "${FULLBOOT_TESTS[@]}"; do tier3_args+=(--tests "$t"); done
  tier3_args+=(--rerun)
  run_step fullboot "컨텍스트 로드 풀부팅 (test-jpa + 기본 프로파일)" "${tier3_args[@]}"
fi

FINGERPRINT="$(gate_fingerprint "$REPO_ROOT")"
python3 - "$REPORT" "$FINGERPRINT" "$VERDICT" "$TIERS" \
  "$(git -C "$REPO_ROOT" rev-parse HEAD)" \
  "$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD)" \
  "$(printf '%s\n' "${STEP_IDS[@]:-}")" \
  "$(printf '%s\n' "${STEP_CMDS[@]:-}")" \
  "$(printf '%s\n' "${STEP_EXITS[@]:-}")" \
  "$(printf '%s\n' "${STEP_SECS[@]:-}")" <<'PY'
import json, sys, datetime
path, fingerprint, verdict, tiers, head, branch, ids, cmds, exits, secs = sys.argv[1:11]
split = lambda raw: [line for line in raw.split("\n") if line]
steps = [
    {"id": i, "cmd": c, "exit": int(e), "durationSec": int(s)}
    for i, c, e, s in zip(split(ids), split(cmds), split(exits), split(secs))
]
json.dump({
    "fingerprint": fingerprint,
    "head": head,
    "branch": branch,
    "tiers": tiers,
    "verdict": verdict,
    "generatedAt": datetime.datetime.now().astimezone().isoformat(timespec="seconds"),
    "steps": steps,
}, open(path, "w"), ensure_ascii=False, indent=2)
PY

printf '\n════ verdict: %s\n' "$VERDICT"
printf '리포트: %s\n' "$REPORT"
[[ "$VERDICT" == PASS ]] || exit 1
printf '지문: %s\n' "$FINGERPRINT"
printf '이 지문 상태 그대로 push·머지하면 훅이 통과시킵니다. 코드를 더 고치면 재실행이 필요합니다.\n'
