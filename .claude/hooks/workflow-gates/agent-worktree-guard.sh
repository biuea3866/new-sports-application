#!/usr/bin/env bash
# agent-worktree-guard.sh
# PreToolUse Agent — 구현 서브에이전트가 워크트리 격리 없이 스폰되는 것을 차단.
#
# /feature 스펙 Step 2.2:
#   "각 에이전트는 isolation: \"worktree\" 모드로 호출하며,
#    현재 시점의 origin/dev HEAD 에서 자기 브랜치를 분기한다."
#
# 배경: PreToolUse Agent 훅이 isolation 필드를 검사하지 않아,
#       구현 에이전트를 worktree 없이 메인에서 스폰해도 통과하던 갭(갭 A).
#       워크트리 병렬성이 0 으로 무너지는 직접 원인.
#
# 이 레포는 워크트리 모델이 2가지다:
#   (1) Agent isolation:"worktree" — 하네스가 워크트리 자동 생성
#   (2) 수동 git worktree add + prompt 에 .claude/worktrees/ 경로 전달
#   → 둘 중 하나라도 충족하면 통과. 둘 다 아니면 차단.
#
# 검사 대상: 구현 에이전트만 (reviewer/explore/tpm/general 등 제외).
# 종료 코드: 0(allow), 2(deny)

set -u

INPUT=$(cat 2>/dev/null || echo "")
[ -z "$INPUT" ] && exit 0

PARSED=$(printf '%s' "$INPUT" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    ti = d.get('tool_input', {})
    sub = ti.get('subagent_type', '') or ''
    iso = ti.get('isolation', '') or ''
    print(sub + '\x1f' + iso)
except Exception:
    print('\x1f')
" 2>/dev/null || printf '\x1f')

SUBAGENT="${PARSED%%$'\x1f'*}"
ISOLATION="${PARSED##*$'\x1f'}"

# 구현 에이전트만 검사. reviewer/explore/tpm/general 등은 worktree 불필요 → 통과.
case "$SUBAGENT" in
  *implementer*|*tdd-implement*|kotlin-spring-impl*|db-schema-writer*|kafka-topic-provisioner*)
    : ;;  # 검사 대상
  *)
    exit 0 ;;  # 그 외 — 통과
esac

# (1) Agent isolation:"worktree" 면 통과
if [ "$ISOLATION" = "worktree" ]; then
  exit 0
fi

# (2) 수동 worktree 모델 — prompt 가 .claude/worktrees/ 경로를 명시하면 통과
PROMPT=$(printf '%s' "$INPUT" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    print(d.get('tool_input', {}).get('prompt', '') or '')
except Exception:
    pass
" 2>/dev/null || echo "")

if echo "$PROMPT" | grep -q '\.claude/worktrees/'; then
  exit 0
fi

# 둘 다 아니면 차단.
cat >&2 <<EOF
BLOCKED: 구현 서브에이전트(${SUBAGENT})는 워크트리 격리 없이 스폰할 수 없습니다.

/feature 스펙 Step 2.2: 모든 구현 에이전트는 워크트리에서 격리 실행해야 합니다.
워크트리 미사용 시 같은 build/ 디렉토리·Gradle daemon 을 공유 → wave 병렬성 0, race condition.

해결 방법 (둘 중 하나):
  1. Agent({ subagent_type: "...", isolation: "worktree", ... })
     → 하네스가 origin/dev HEAD 에서 워크트리를 자동 분기.
  2. 미리 git worktree add 한 워크트리 경로를 prompt 에 명시:
     "당신은 .claude/worktrees/<name> 안에서만 작업합니다. cd .claude/worktrees/<name> ..."

reviewer/explore/tpm/general 류 에이전트는 이 검사 대상이 아닙니다.
EOF
exit 2
