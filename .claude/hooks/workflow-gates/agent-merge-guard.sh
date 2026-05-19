#!/usr/bin/env bash
# agent-merge-guard.sh
# PreToolUse Agent — 서브에이전트가 PR 머지를 직접 수행하지 못하게 차단.
#
# /feature 스펙(Step 2 + Step 4-B): 머지는 메인 오케스트레이터의 책임이며,
# 서브에이전트는 push + PR 생성까지만 한다. pr-reviewer 호출 후 메인이 머지.
#
# 차단 조건:
#   Agent prompt 본문에 다음 패턴이 포함되면 deny.
#     gh pr merge / gh api .../merge / git merge --ff (feature 브랜치)
#
# 종료 코드: 0(allow), 2(deny)

set -u

INPUT=$(cat 2>/dev/null || echo "")

if [ -z "$INPUT" ]; then
  exit 0
fi

PROMPT=$(printf '%s' "$INPUT" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    p = d.get('tool_input', {}).get('prompt', '')
    print(p)
except Exception:
    pass
" 2>/dev/null || echo "")

if [ -z "$PROMPT" ]; then
  exit 0
fi

# 금지 패턴
BLOCK_PATTERN='gh[[:space:]]+pr[[:space:]]+merge|gh[[:space:]]+api[[:space:]]+.+/merge|git[[:space:]]+merge[[:space:]]+--ff-only[[:space:]]+(dev|main|master)'

if echo "$PROMPT" | grep -qE "$BLOCK_PATTERN"; then
  cat >&2 <<'EOF'
BLOCKED: 서브에이전트가 PR 머지를 직접 수행할 수 없습니다.

/feature 파이프라인 스펙 (Step 2 + Step 4-B):
  - 서브에이전트 책임: push + gh pr create 까지
  - 메인 오케스트레이터 책임: pr-reviewer 호출 → verdict 확인 → gh pr merge

해결 방법:
  Agent prompt 에서 `gh pr merge` 단계를 제거하세요.
  PR 생성까지만 지시하고, 머지는 메인 세션이 pr-reviewer 결과 확인 후 수행합니다.
EOF
  exit 2
fi

exit 0
