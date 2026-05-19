#!/usr/bin/env bash
# pr-review-gate.sh
# PreToolUse Bash — `gh pr merge` 명령에 대해 pr-reviewer 호출 기록 확인.
#
# /feature 스펙 Step 4-B:
#   "각 PR마다 pr-reviewer 호출은 의무. PR 생성 직후 호출하지 않으면 머지 금지."
#
# 차단 조건:
#   `gh pr merge <N>` 실행 시 .claude/cache/pr-reviews.json 에 PR <N> 의
#   pr-reviewer 호출 기록이 없으면 deny.
#
# pr-reviewer 호출 기록은 pr-review-tracker.sh (PostToolUse Agent) 가
# subagent_type=pr-reviewer 호출을 감지해 자동 갱신한다.
#
# 종료 코드: 0(allow), 2(deny)

set -u

INPUT=$(cat 2>/dev/null || echo "")
[ -z "$INPUT" ] && exit 0

CMD=$(printf '%s' "$INPUT" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    print(d.get('tool_input', {}).get('command', ''))
except Exception:
    pass
" 2>/dev/null || echo "")

# `gh pr merge` 가 실제 실행 명령으로 쓰인 경우만 검사.
# 명령 시작 / 파이프·세미콜론·&& 뒤에 올 때만 인정 — 문자열 리터럴(python3 -c "... gh pr merge ...")
# 이나 cat/grep 으로 hook 내용 출력 시의 임베드 문자열은 통과시켜 false-positive 차단.
if ! echo "$CMD" | grep -qE '(^|[;&|]|&&[[:space:]]*|\|\|[[:space:]]*)[[:space:]]*gh[[:space:]]+pr[[:space:]]+merge\b'; then
  exit 0
fi

# PR 번호 파싱: `gh pr merge 5 --squash` 또는 `gh pr merge --squash 5` 모두 지원
PR_NUM=$(echo "$CMD" | python3 -c "
import re, sys
cmd = sys.stdin.read()
m = re.search(r'gh\s+pr\s+merge(?:\s+--[^\s]+)*\s+(\d+)', cmd)
if not m:
    m = re.search(r'gh\s+pr\s+merge\s+(\d+)', cmd)
print(m.group(1) if m else '')
" 2>/dev/null || echo "")

if [ -z "$PR_NUM" ]; then
  # PR 번호 없는 호출 (현재 브랜치 PR auto-detect 등) — gh 가 알아서 처리, 차단 안 함
  exit 0
fi

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
REVIEW_LOG="${PROJECT_DIR}/.claude/cache/pr-reviews.json"

if [ ! -f "$REVIEW_LOG" ]; then
  cat >&2 <<EOF
BLOCKED: PR #${PR_NUM} 머지 전 pr-reviewer 호출 필요.

/feature 스펙 Step 4-B:
  "각 PR마다 pr-reviewer 호출은 의무. PR 생성 직후 호출하지 않으면 머지 금지."

기록 파일이 없습니다: ${REVIEW_LOG}
pr-reviewer 서브에이전트를 PR #${PR_NUM} 에 대해 먼저 호출한 후 머지하세요.
EOF
  exit 2
fi

REVIEWED=$(python3 -c "
import json
try:
    with open('${REVIEW_LOG}') as f:
        d = json.load(f)
    print('yes' if '${PR_NUM}' in d.get('reviewedPrs', {}) else 'no')
except Exception:
    print('no')
" 2>/dev/null || echo "no")

if [ "$REVIEWED" != "yes" ]; then
  cat >&2 <<EOF
BLOCKED: PR #${PR_NUM} 머지 전 pr-reviewer 호출 필요.

/feature 스펙 Step 4-B:
  "각 PR마다 pr-reviewer 호출은 의무. 호출 결과(REQUEST_CHANGES/APPROVED/COMMENT)를
   transcript 에 명시한 뒤에만 다음 단계 진행."

해결 방법:
  Agent({ subagent_type: "pr-reviewer", description: "Review PR #${PR_NUM}", ... })
  로 호출하고 verdict 를 받은 후 머지하세요.

pr-reviewer 가 호출됐다면 .claude/cache/pr-reviews.json 갱신을 확인하세요
(pr-review-tracker.sh hook 동작 여부).
EOF
  exit 2
fi

exit 0
