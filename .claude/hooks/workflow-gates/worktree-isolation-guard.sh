#!/usr/bin/env bash
# worktree-isolation-guard.sh
# PreToolUse Bash — 서브에이전트가 main worktree (/Users/biuea/sports-application) 로
# 절대 경로 cd 또는 git 작업을 수행하는 패턴 차단.
#
# /feature 스펙 Step 2 (Wave 스케줄러):
#   "각 에이전트는 isolation: \"worktree\" 모드로 호출하며,
#    현재 시점의 origin/dev HEAD 에서 자기 브랜치(feat/<티켓번호>)를 분기"
#
# 동작:
#   - 서브에이전트 컨텍스트(CLAUDE_AGENT_TYPE 또는 CLAUDE_PROJECT_DIR 가
#     .claude/worktrees/agent-* 하위)에서 main worktree 경로로 cd / git -C 차단
#   - 메인 세션(CLAUDE_PROJECT_DIR = /Users/biuea/sports-application)에서는 통과
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

[ -z "$CMD" ] && exit 0

# 현재 컨텍스트가 격리 worktree 인지 — pwd 기반 자체 판단
CURRENT_DIR="$(pwd 2>/dev/null || echo "")"
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-/Users/biuea/sports-application}"

# CLAUDE_PROJECT_DIR 가 격리 worktree (.claude/worktrees/*) 일 때만 활성.
# agent-* (하네스 자동) 와 수동 git worktree add (mcp-* 등) 둘 다 포함.
case "$PROJECT_DIR" in
  */.claude/worktrees/*)
    IS_AGENT_WORKTREE=1
    ;;
  *)
    IS_AGENT_WORKTREE=0
    ;;
esac

# 메인 세션 컨텍스트면 통과
[ "$IS_AGENT_WORKTREE" -eq 0 ] && exit 0

# 금지 패턴: main worktree 절대 경로 진입 / git -C main 경로 / Edit 절대 경로
MAIN_PATH_RE='/Users/biuea/sports-application(/backend|/web|/mobile)?(/\S*)?($|[[:space:]])'

# 절대 경로 cd
if echo "$CMD" | grep -qE "cd[[:space:]]+${MAIN_PATH_RE}"; then
  cat >&2 <<EOF
BLOCKED: 서브에이전트가 main worktree 절대 경로로 cd 하는 패턴 차단.

서브에이전트는 자기 격리 worktree (\$PWD) 안에서만 작업해야 합니다.
현재 worktree: ${PROJECT_DIR}

대신:
  - 상대 경로 사용 (cd backend, ./gradlew ...)
  - 또는 cd "${PROJECT_DIR}/backend"

원인: main worktree 진입 시 다른 wave agent 와 build/ 디렉토리·Gradle daemon 을 공유 → 직렬화·race condition.
EOF
  exit 2
fi

# git -C main 경로
if echo "$CMD" | grep -qE "git[[:space:]]+-C[[:space:]]+${MAIN_PATH_RE}"; then
  cat >&2 <<EOF
BLOCKED: 서브에이전트가 main worktree 경로로 git -C 실행 패턴 차단.

서브에이전트는 자기 격리 worktree 안에서만 git 작업해야 합니다.
현재 worktree: ${PROJECT_DIR}
EOF
  exit 2
fi

# Gradle wrapper main 경로 직접 호출
if echo "$CMD" | grep -qE "/Users/biuea/sports-application/backend/gradlew\b"; then
  cat >&2 <<EOF
BLOCKED: main worktree 의 gradlew 직접 호출 차단.

자기 격리 worktree 의 gradlew 를 사용하세요:
  cd backend && ./gradlew ...
  또는
  ${PROJECT_DIR}/backend/gradlew ...
EOF
  exit 2
fi

exit 0
