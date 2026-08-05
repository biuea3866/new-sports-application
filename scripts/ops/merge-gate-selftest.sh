#!/usr/bin/env bash
# 머지 게이트 자기검증 하네스 (R-21·R-24 대응).
#
# 이 스크립트는 "게이트가 실제로 막는가"를 증명한다. 게이트를 신뢰하는 근거는
# 게이트 코드를 읽은 것이 아니라 **고의 위반이 차단되는 것을 관측한 기록**이다.
#
#   --fast  훅 판정 케이스만 (C1~C5, gradle 미실행 — 수 초)
#   --full  + 게이트 실행 케이스 (C6~C9, gradle·컨테이너 실행 — 수십 분)
#
# 각 케이스는 위반을 주입한 뒤 반드시 원복한다(trap). 원복 실패 시 비-0 으로 끝내고
# 남은 잔재를 출력한다 — 조용히 더럽히지 않는다.
set -uo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly GATE="$REPO_ROOT/scripts/ops/merge-gate.sh"
readonly HOOK="$REPO_ROOT/.claude/hooks/merge-gate-verify.sh"
readonly REPORT="$REPO_ROOT/backend/build/merge-gate/report.json"

MODE="${1:---fast}"
PASS_COUNT=0
FAIL_COUNT=0
declare -a FAILURES=()
declare -a INJECTED_FILES=()

log()  { printf '%s\n' "$*"; }
head2() { printf '\n──── %s\n' "$*"; }

cleanup_injected() {
  local leftover=0
  for f in "${INJECTED_FILES[@]:-}"; do
    [[ -z "$f" ]] && continue
    rm -f "$f" || leftover=1
    # 주입용으로 만든 빈 디렉토리도 치운다 (git 은 빈 디렉토리를 추적하지 않아 지문에는 안 걸리지만
    # 소스 트리에 selftest/ 잔재를 남기지 않는다). 비어 있지 않으면 실패해도 무시한다.
    rmdir "$(dirname "$f")" 2>/dev/null || true
  done
  # 주입은 신규 파일 생성만 사용한다(기존 파일 수정 금지) — 추적 파일이 더러워지면 즉시 알린다.
  local dirty
  dirty="$(cd "$REPO_ROOT" && git status --porcelain -- backend scripts .claude | grep -v '^?? ' || true)"
  if [[ -n "$dirty" ]]; then
    log "🛑 자기검증이 추적 파일을 변경한 상태로 끝났습니다 — 수동 확인 필요:"
    printf '%s\n' "$dirty"
    leftover=1
  fi
  return $leftover
}
trap 'cleanup_injected || exit 1' EXIT

assert_eq() { # <설명> <기대> <실제>
  local what="$1" want="$2" got="$3"
  if [[ "$want" == "$got" ]]; then
    PASS_COUNT=$((PASS_COUNT + 1)); log "  ✅ $what (기대=$want 실제=$got)"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1)); FAILURES+=("$what — 기대=$want 실제=$got"); log "  ❌ $what (기대=$want 실제=$got)"
  fi
}

# 훅을 실제 호출 형태(stdin JSON)로 실행하고 exit code 를 돌려준다.
# 두 번째 인자로 세션 cwd 를 바꿀 수 있다 (worktree 오인 케이스 검증용).
run_hook() { # <command> [세션cwd]
  local cmd="$1" session_cwd="${2:-$REPO_ROOT}" payload
  payload=$(python3 - "$cmd" "$session_cwd" <<'PY'
import json, sys
print(json.dumps({"tool_input": {"command": sys.argv[1]}, "cwd": sys.argv[2]}))
PY
)
  printf '%s' "$payload" | "$HOOK" >/dev/null 2>&1
  printf '%s' $?
}

write_report() { # <verdict> <fingerprint> [tiers]
  mkdir -p "$(dirname "$REPORT")"
  python3 - "$REPORT" "$1" "$2" "${3:-1,2,3}" <<'PY'
import json, sys
path, verdict, fingerprint, tiers = sys.argv[1:5]
json.dump({"fingerprint": fingerprint, "head": "selftest", "verdict": verdict,
           "tiers": tiers, "generatedAt": "selftest", "steps": []}, open(path, "w"))
PY
}

require_scripts() {
  local missing=0
  [[ -x "$GATE" ]] || { log "🛑 게이트 스크립트 없음/실행권한 없음: $GATE"; missing=1; }
  [[ -x "$HOOK" ]] || { log "🛑 검증 훅 없음/실행권한 없음: $HOOK"; missing=1; }
  return $missing
}

# ── C1~C5: 훅 판정 (gradle 미실행) ────────────────────────────────────────────
run_hook_cases() {
  local fp
  fp="$("$GATE" --print-fingerprint)" || { log "🛑 --print-fingerprint 실패"; FAIL_COUNT=$((FAIL_COUNT+1)); return; }

  head2 "C1. 리포트 부재 → push 차단"
  rm -f "$REPORT"
  assert_eq "리포트 없으면 git push 차단(exit 2)" 2 "$(run_hook 'git push origin HEAD   # tests-passed')"

  head2 "C2. verdict=FAIL 리포트 → 차단"
  write_report FAIL "$fp"
  assert_eq "verdict FAIL 이면 차단(exit 2)" 2 "$(run_hook 'git push origin HEAD   # tests-passed')"

  head2 "C3. fingerprint 불일치(게이트 후 코드 변경) → 차단"
  write_report PASS "stale-fingerprint-0000"
  assert_eq "fingerprint 불일치면 차단(exit 2)" 2 "$(run_hook 'git push origin HEAD   # tests-passed')"

  head2 "C4. 유효 리포트(PASS + fingerprint 일치) → 통과"
  write_report PASS "$fp"
  assert_eq "유효 리포트면 push 통과(exit 0)" 0 "$(run_hook 'git push origin HEAD   # tests-passed')"
  assert_eq "유효 리포트면 gh pr merge 통과(exit 0)" 0 "$(run_hook 'gh pr merge 400 --squash   # p3-reflected')"

  head2 "C4b. 일부 tier 만 실행한 리포트 → 차단"
  write_report PASS "$fp" "1"
  assert_eq "tier 1 만 돈 리포트는 차단(exit 2)" 2 "$(run_hook 'git push origin HEAD   # tests-passed')"
  write_report PASS "$fp" "1,2"
  assert_eq "tier 3 누락 리포트는 차단(exit 2)" 2 "$(run_hook 'git push origin HEAD   # tests-passed')"

  head2 "C4c. 손상된 리포트 → 차단"
  printf 'not json at all' > "$REPORT"
  assert_eq "리포트 파싱 불가면 차단(exit 2)" 2 "$(run_hook 'git push origin HEAD   # tests-passed')"

  head2 "C5. 게이트 대상 아닌 명령·변경은 통과"
  rm -f "$REPORT"
  assert_eq "git status 는 게이트 대상 아님(exit 0)" 0 "$(run_hook 'git status')"

  # 다른 worktree 에서 push 할 때 세션 cwd 의 리포트로 통과하면 안 된다.
  # 세션 cwd 를 이 레포로 두고, 명령은 리포트가 없는 **다른 디렉토리**를 가리키게 만든다.
  head2 "C5b. 명령이 가리키는 디렉토리를 기준으로 판정한다 (worktree 오인 방지)"
  local other_repo="$REPO_ROOT/backend/build/merge-gate/selftest-other-worktree"
  rm -rf "$other_repo"; mkdir -p "$other_repo"
  git -C "$other_repo" init --quiet
  # 게이트 대상(kt)이 있고 리포트는 없는 별개 레포 — 차단돼야 한다.
  mkdir -p "$other_repo/backend/scripts" "$other_repo/scripts/ops/lib"
  cp "$REPO_ROOT/scripts/ops/merge-gate.sh" "$other_repo/scripts/ops/"
  cp "$REPO_ROOT/scripts/ops/lib/gate-fingerprint.sh" "$other_repo/scripts/ops/lib/"
  printf 'package x\n' > "$other_repo/backend/Probe.kt"
  write_report PASS "$("$GATE" --print-fingerprint)"   # 세션 cwd 쪽에는 유효 리포트를 둔다
  assert_eq "cd 로 다른 레포를 가리키면 그 레포 기준으로 차단(exit 2)" 2 \
    "$(run_hook "cd $other_repo && git push origin HEAD   # tests-passed" "$REPO_ROOT")"
  assert_eq "git -C 로 다른 레포를 가리켜도 차단(exit 2)" 2 \
    "$(run_hook "git -C $other_repo push origin HEAD   # tests-passed" "$REPO_ROOT")"
  rm -rf "$other_repo"
  rm -f "$REPORT"

  head2 "C3b. 게이트 후 코드가 바뀌면 유효 리포트가 무효화된다"
  write_report PASS "$fp"
  local scratch="$REPO_ROOT/backend/common/src/main/kotlin/com/sportsapp/SelftestFingerprintProbe.kt"
  INJECTED_FILES+=("$scratch")
  printf 'package com.sportsapp\n\ninternal object SelftestFingerprintProbe\n' > "$scratch"
  assert_eq "kt 파일 추가 후에는 같은 리포트로 통과 못 함(exit 2)" 2 "$(run_hook 'git push origin HEAD   # tests-passed')"
  rm -f "$scratch"
  assert_eq "원복하면 다시 통과(exit 0)" 0 "$(run_hook 'git push origin HEAD   # tests-passed')"
  rm -f "$REPORT"
}

# ── C6~C9: 게이트 실행 (gradle·컨테이너) ──────────────────────────────────────
# 각 케이스는 위반 파일을 주입하고 해당 tier 만 돌려 "비-0 종료 + verdict=FAIL"을 확인한다.
run_gate_case() { # <설명> <tier> <파일경로> <파일내용>
  local what="$1" tier="$2" path="$3" body="$4"
  head2 "$what"
  mkdir -p "$(dirname "$path")"
  INJECTED_FILES+=("$path")
  printf '%s' "$body" > "$path"
  local rc=0
  "$GATE" --tier="$tier" >/dev/null 2>&1 || rc=$?
  rm -f "$path"
  [[ "$rc" != "0" ]] && rc=nonzero
  assert_eq "$what → 게이트가 비-0 으로 차단" nonzero "$rc"
}

run_gate_cases() {
  local src="$REPO_ROOT/backend/commerce/src/main/kotlin/com/sportsapp/selftest"

  run_gate_case "C6. harnessCheck 위반(!! 사용)" 1 "$src/SelftestNonNullAssertion.kt" \
'package com.sportsapp.selftest

internal object SelftestNonNullAssertion {
    fun probe(value: String?): String = value!!
}
'

  run_gate_case "C7. detekt 위반(ThrowsCount 3개 — R-21 유입 유형)" 1 "$src/SelftestThrowsCount.kt" \
'package com.sportsapp.selftest

internal object SelftestThrowsCount {
    fun probe(flag: Int) {
        if (flag == 1) throw IllegalStateException("one")
        if (flag == 2) throw IllegalStateException("two")
        if (flag == 3) throw IllegalStateException("three")
    }
}
'

  # 컴파일은 통과하고(같은 모듈이라 참조 가능) ArchUnit U-01 만 잡는 위반이어야 의미가 있다.
  run_gate_case "C8. 아키텍처 위반(domain → infrastructure 의존, U-01)" 2 \
    "$REPO_ROOT/backend/commerce/src/main/kotlin/com/sportsapp/domain/goods/SelftestLayerViolation.kt" \
'package com.sportsapp.domain.goods

import com.sportsapp.infrastructure.lock.SeatLockStoreImpl

internal class SelftestLayerViolation(private val seatLockStore: SeatLockStoreImpl)
'

  # R-24 는 `FacilityDomainService` 주입이었지만, 그 수정으로 `TestJpaGatewayStubConfig` 에
  # test-jpa 스텁이 생겨 **그 빈으로는 더 이상 재현되지 않는다**(초기 케이스가 이 이유로 통과했다).
  # 재현에는 스텁되지 않은 프로파일 게이트 빈이 필요하다 — `FacilityStatsDomainService` 를 쓴다.
  # 스텁 목록이 늘어 이 케이스가 통과해버리면 대상 빈을 다시 골라야 한다(그때도 실패 원인은 이 주석).
  run_gate_case "C9. 프로파일 게이트 빈 주입(R-24 유형 — test-jpa 컨텍스트 붕괴)" 3 \
    "$REPO_ROOT/backend/bootstrap/src/main/kotlin/com/sportsapp/selftest/SelftestProfileGatedInjection.kt" \
'package com.sportsapp.selftest

import com.sportsapp.domain.facility.service.FacilityStatsDomainService
import org.springframework.stereotype.Service

// 프로파일 무관 빈이 @Profile("!test-jpa") 빈을 직접 주입 — R-24 와 동일 구조.
@Service
class SelftestProfileGatedInjection(private val facilityStatsDomainService: FacilityStatsDomainService)
'
}

# ── 실행 ──────────────────────────────────────────────────────────────────────
log "머지 게이트 자기검증 ($MODE)"
log "repo: $REPO_ROOT"

if ! require_scripts; then
  log ""
  log "RED — 게이트 구현이 없습니다. 자기검증은 게이트 구현 이후에 통과해야 합니다."
  exit 1
fi

run_hook_cases
[[ "$MODE" == "--full" ]] && run_gate_cases

log ""
log "════ 결과: 통과 $PASS_COUNT · 실패 $FAIL_COUNT"
if (( FAIL_COUNT > 0 )); then
  for f in "${FAILURES[@]}"; do log "  ❌ $f"; done
  exit 1
fi
log "게이트가 위 케이스 전부를 의도대로 처리했습니다."
