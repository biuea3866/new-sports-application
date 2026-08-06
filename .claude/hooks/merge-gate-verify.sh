#!/usr/bin/env bash
# PreToolUse hook (Bash matcher) — 머지 게이트 아티팩트 검증 (R-21·R-24 대응).
#
# 글로벌 훅(private-push-test.sh·private-auto-merge-gate.sh)은 `# tests-passed`·`# p3-reflected`
# **토큰 명예제**다 — 실행 여부를 검증하지 않으므로 "돌렸다"는 단언만으로 통과한다.
# 2026-08-01~03 PR 7건이 각각 main 에 red 를 남긴 것이 그 구조의 결과다.
#
# 이 훅은 단언 대신 **아티팩트**를 본다: scripts/ops/merge-gate.sh 가 남긴 리포트가
#   ① 존재하고 ② verdict=PASS 이고 ③ 지문이 지금 코드 상태와 일치하는가.
# 우회 토큰은 없다. 게이트를 돌리지 않으면 통과할 방법이 없다.
#
# 대상: `git push`, `gh pr merge`
# 스킵: Kotlin·Gradle·정적분석 설정이 안 바뀐 변경(문서·FE·compose 전용)은 게이트를 요구하지 않는다.
set -uo pipefail

input=$(cat)
command=$(printf '%s' "$input" | python3 -c "import json,sys;print(json.load(sys.stdin).get('tool_input',{}).get('command',''))" 2>/dev/null || true)
hook_cwd=$(printf '%s' "$input" | python3 -c "import json,sys;print(json.load(sys.stdin).get('cwd',''))" 2>/dev/null || true)

[[ -z "$command" ]] && exit 0
[[ -z "$hook_cwd" ]] && exit 0

# 대상 명령만 — git push / gh pr merge (옵션 변형 포함)
is_target=1
[[ "$command" =~ (^|[[:space:]\;\&\|])git([[:space:]]+-[A-Za-z-]+([[:space:]]+[^[:space:]]+)?)*[[:space:]]+push([[:space:]]|$) ]] && is_target=0
[[ "$command" =~ (^|[[:space:]\;\&\|])gh([[:space:]]+-[A-Za-z]+[[:space:]]+[^[:space:]]+)*[[:space:]]+pr[[:space:]]+merge([[:space:]]|$) ]] && is_target=0
(( is_target == 0 )) || exit 0

# 검사 대상 디렉토리를 명령에서 되짚는다.
#
# hook 이 받는 `cwd` 는 **세션 cwd** 이고, 이 레포는 worktree 를 여러 개 병행한다
# (`cd <worktree> && git push …`, `git -C <worktree> push …`). 세션 cwd 만 보면 push 하는
# worktree 가 아니라 **다른 worktree 의 리포트**를 검사해 잘못 통과시킬 수 있다 —
# 지문이 우연히 맞을 일은 없지만, 게이트를 돌린 worktree 의 PASS 리포트로 게이트를 돌리지 않은
# worktree 의 push 가 통과하는 조합이 실제로 가능하다. 그래서 명령에 적힌 디렉토리를 우선한다.
target_dir="$hook_cwd"
git_c_dir=$(printf '%s' "$command" | sed -nE 's/.*git[[:space:]]+-C[[:space:]]+([^[:space:];&|]+).*/\1/p' | tail -1)
cd_dir=$(printf '%s' "$command" | sed -nE 's/(^|.*[;&|][[:space:]]*)cd[[:space:]]+([^[:space:];&|]+).*/\2/p' | tail -1)
for candidate in "$git_c_dir" "$cd_dir"; do
  [[ -z "$candidate" ]] && continue
  # 상대 경로는 세션 cwd 기준으로 해석한다.
  [[ "$candidate" != /* ]] && candidate="$hook_cwd/$candidate"
  [[ -d "$candidate" ]] && target_dir="$candidate"
done

repo_root=$(git -C "$target_dir" rev-parse --show-toplevel 2>/dev/null || true)
[[ -z "$repo_root" ]] && exit 0

lib="$repo_root/scripts/ops/lib/gate-fingerprint.sh"
gate="$repo_root/scripts/ops/merge-gate.sh"
# 이 훅과 게이트는 같은 커밋에 함께 있어야 의미가 있다. 없는 브랜치(게이트 도입 이전)는 비켜선다.
[[ -f "$lib" && -f "$gate" ]] || exit 0
source "$lib"

# 게이트 대상 변경이 아니면 통과 (문서·FE 전용 push)
base_ref=origin/main
git -C "$repo_root" rev-parse --verify --quiet "$base_ref" >/dev/null 2>&1 || base_ref=HEAD
gate_required "$repo_root" "$base_ref" || exit 0

report="$repo_root/backend/build/merge-gate/report.json"
gate_hint="cd $repo_root && ./scripts/ops/merge-gate.sh"

deny() { # <요약> <상세...>
  {
    printf '🛑 머지 게이트 차단 — %s\n\n' "$1"
    shift
    printf '%s\n' "$@"
    printf '\n게이트를 실행하세요 (정적분석 8모듈 + 아키텍처 규칙 + 컨텍스트 로드 풀부팅):\n  %s\n' "$gate_hint"
    printf '\n이 훅에는 우회 토큰이 없습니다 — 게이트 리포트가 유일한 통과 근거입니다.\n'
    printf '근거: R-21(detekt 4연속 통과)·R-24(test-jpa 풀부팅 73클래스 붕괴), 후속-리스크-등록부.md\n'
  } >&2
  exit 2
}

[[ -f "$report" ]] || deny "게이트 리포트가 없습니다" \
  "Kotlin·Gradle 변경이 포함된 push·머지는 게이트 리포트를 요구합니다." \
  "리포트 경로: $report"

read -r report_verdict report_fingerprint report_tiers report_at < <(
  python3 - "$report" <<'PY' 2>/dev/null || true
import json, sys
try:
    d = json.load(open(sys.argv[1]))
except Exception:
    print("UNREADABLE - - -"); raise SystemExit(0)
print(d.get("verdict", "?"), d.get("fingerprint", "-"), d.get("tiers", "-"), d.get("generatedAt", "-"))
PY
)

[[ "${report_verdict:-}" == "UNREADABLE" || -z "${report_verdict:-}" ]] && \
  deny "게이트 리포트를 읽을 수 없습니다" "리포트가 손상됐습니다. 게이트를 다시 실행하세요." "리포트 경로: $report"

[[ "$report_verdict" == "PASS" ]] || deny "게이트 verdict 가 PASS 가 아닙니다 (verdict=$report_verdict)" \
  "실패 단계 로그: $repo_root/backend/build/merge-gate/logs/" \
  "지적된 위반을 고친 뒤 게이트를 다시 실행하세요."

[[ "$report_tiers" == "1,2,3" ]] || deny "게이트가 일부 tier 만 실행됐습니다 (tiers=$report_tiers)" \
  "push·머지에는 전 tier(1,2,3)가 필요합니다. --tier 는 자기검증 전용입니다."

current_fingerprint="$(gate_fingerprint "$repo_root")"
[[ "$current_fingerprint" == "$report_fingerprint" ]] || deny "게이트 실행 이후 코드가 변경됐습니다" \
  "리포트 지문: $report_fingerprint (생성 $report_at)" \
  "현재 지문:   $current_fingerprint" \
  "검사받지 않은 코드가 나가는 것을 막습니다 — 게이트를 다시 실행하세요."

exit 0
