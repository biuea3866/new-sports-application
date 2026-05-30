#!/usr/bin/env bash
# worktree-quota-guard.sh
# PreToolUse (Bash + Agent) — 활성 워크트리 개수가 임계를 넘으면 신규 생성을 차단.
#
# 배경: 작업자(병렬 티켓/서브에이전트)가 워크트리를 만들고 작업 후 제거하지 않아
#       .claude/worktrees/ 에 수십~수백 개가 누적되는 현상. 누적 워크트리는
#       디스크·git 메타데이터·Gradle 캐시를 잠식하고 dev 머지 추적을 흐린다.
#
# 동작:
#   - Bash:  `git worktree add ...` 호출 직전 검사
#   - Agent: isolation:"worktree" 스폰 직전 검사
#   둘 다, 현재 활성 워크트리 수(메인 제외)가 WORKTREE_QUOTA 이상이면 차단하고
#   cleanup-worktrees.sh 실행을 먼저 요구한다.
#
# 임계: 환경변수 WORKTREE_QUOTA (기본 80). 자동정리(SessionStart)가 평소 수를
#       낮게 유지하므로, 이 임계는 "정리 누락이 폭주한" 상태를 잡는 백스톱이다.
#
# 종료 코드: 0(allow), 2(deny)

set -u

INPUT=$(cat 2>/dev/null || echo "")
[ -z "$INPUT" ] && exit 0

QUOTA="${WORKTREE_QUOTA:-80}"

# tool_name / command / isolation 파싱
PARSED=$(printf '%s' "$INPUT" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    tn = d.get('tool_name', '') or ''
    ti = d.get('tool_input', {}) or {}
    cmd = ti.get('command', '') or ''
    iso = ti.get('isolation', '') or ''
    print(tn + '\x1f' + iso + '\x1f' + cmd.replace('\n', ' '))
except Exception:
    print('\x1f\x1f')
" 2>/dev/null || printf '\x1f\x1f')

TOOL_NAME="${PARSED%%$'\x1f'*}"
REST="${PARSED#*$'\x1f'}"
ISOLATION="${REST%%$'\x1f'*}"
CMD="${REST#*$'\x1f'}"

# 이 호출이 "워크트리 신규 생성" 인지 판정
CREATES_WORKTREE=0
case "$TOOL_NAME" in
  Bash)
    # git worktree add (prune/remove/list 는 정리/조회이므로 제외)
    # 명령 시작(^) 또는 구분자(; & |) 뒤에 올 때만 매칭 — PR 본문/커밋 메시지/echo
    # 안에 "git worktree add" 문자열이 있어도 오탐하지 않도록 위치를 고정한다.
    if echo "$CMD" | grep -qE '(^|[;&|])[[:space:]]*git[[:space:]]+worktree[[:space:]]+add\b'; then
      CREATES_WORKTREE=1
    fi
    ;;
  Agent)
    [ "$ISOLATION" = "worktree" ] && CREATES_WORKTREE=1
    ;;
esac

[ "$CREATES_WORKTREE" -eq 0 ] && exit 0

# 현재 활성 워크트리 수 (메인 worktree 1개 제외)
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-/Users/biuea/sports-application}"
TOTAL=$(git -C "$PROJECT_DIR" worktree list 2>/dev/null | wc -l | tr -d ' ')
[ -z "$TOTAL" ] && exit 0
ACTIVE=$((TOTAL - 1))
[ "$ACTIVE" -lt 0 ] && ACTIVE=0

if [ "$ACTIVE" -lt "$QUOTA" ]; then
  exit 0
fi

cat >&2 <<EOF
BLOCKED: 활성 워크트리가 ${ACTIVE}개로 임계(${QUOTA})를 넘어 신규 워크트리 생성을 차단합니다.

작업자가 워크트리를 만들고 제거하지 않아 누적된 상태입니다. 먼저 정리하세요:

  # 1) 무엇이 안전하게 정리되는지 미리 확인 (dry-run)
  bash ${PROJECT_DIR}/.claude/hooks/util/cleanup-worktrees.sh

  # 2) 병합+clean+unlocked 워크트리 실제 제거
  bash ${PROJECT_DIR}/.claude/hooks/util/cleanup-worktrees.sh --execute

정리 후에도 임계를 넘으면, 끝나지 않은 작업이 그만큼 많다는 신호입니다.
미머지 작업을 dev 까지 마무리(PR→머지)하거나, 임시로 WORKTREE_QUOTA 환경변수를 높이세요.
(자동정리는 SessionStart 마다 병합+clean 워크트리를 제거하므로 평소엔 임계 아래로 유지됩니다.)
EOF
exit 2
