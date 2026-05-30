#!/usr/bin/env bash
# qa-reverify-gate.sh
# PreToolUse Bash — `/qa` 파이프라인의 Step 6(환경 정리)을 강제 게이트한다.
#
# `docker-compose -f qa/e2e/docker-compose.qa.yml down` 명령이 실행되려 할 때,
# Step 5(fix 리뷰 + 재검증) 산출물인 reverify-report.md 가 존재하는지 검사한다.
# 없으면 차단 — "결함 fix PR 생성 = QA 완료"라는 거짓 완전성을 막는다.
#
# 통과 조건:
#   - .analysis/outputs/qa/ 자체가 없음 → /qa 컨텍스트 아님, 통과
#   - 최신 토픽 디렉토리에 reverify-report.md 존재 → 통과
#   - 격리 worktree 컨텍스트 → 통과 (재검증은 메인 세션 책임)
#
# 차단:
#   - qa 산출물 디렉토리는 있는데 reverify-report.md 가 없음 → deny
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

# qa docker-compose down 명령이 아니면 통과
echo "$CMD" | grep -qE 'docker-compose.*qa/e2e/docker-compose\.qa\.yml.*down' || \
  echo "$CMD" | grep -qE 'docker compose.*qa/e2e/docker-compose\.qa\.yml.*down' || exit 0

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-/Users/biuea/sports-application}"

# 격리 worktree 컨텍스트는 통과 — 재검증은 메인 세션 책임
case "$PROJECT_DIR" in
  */.claude/worktrees/agent-*)
    exit 0
    ;;
esac

cd "$PROJECT_DIR" 2>/dev/null || exit 0

# qa 산출물 디렉토리 자체가 없으면 /qa 컨텍스트 아님 — 통과
[ -d ".analysis/outputs/qa" ] || exit 0

# 최신 토픽 디렉토리 찾기
LATEST_TOPIC=$(ls -dt .analysis/outputs/qa/*/ 2>/dev/null | head -1)
[ -z "$LATEST_TOPIC" ] && exit 0

# reverify-report.md 존재 확인
if [ ! -f "${LATEST_TOPIC}reverify-report.md" ]; then
  cat >&2 <<EOF
BLOCKED: /qa 환경 정리(docker down)를 차단합니다 — 재검증 산출물이 없습니다.

검색 경로: ${LATEST_TOPIC}reverify-report.md

/qa 파이프라인은 Step 5(fix 리뷰 + 재검증)를 거쳐야 종료할 수 있습니다.
"결함 fix PR 생성"만으로 QA를 끝내면 안 됩니다 — fix가 실제로 결함을 해결했는지,
새 회귀를 만들지 않았는지 재검증해야 합니다 (.claude/commands/qa.md Step 5).

해결:
  1. Step 5-A — code-reviewer로 각 fix 리뷰
  2. Step 5-B — fix 통합 브랜치에서 Step 1~3 재실행, 결함 해결·회귀 확인
  3. 결과를 ${LATEST_TOPIC}reverify-report.md 에 기록
     - 재검증 대상이 0건이어도 "재검증 대상 0건 — 생략" 사유를 명시적으로 기록

재검증 후 다시 docker-compose down 을 실행하세요.
EOF
  exit 2
fi

# reverify-report.md 가 비어 있으면 거짓 산출물 — deny
if [ ! -s "${LATEST_TOPIC}reverify-report.md" ]; then
  cat >&2 <<EOF
BLOCKED: reverify-report.md 가 비어 있습니다 (빈 placeholder 금지).

경로: ${LATEST_TOPIC}reverify-report.md

재검증 결과(결함 ID / 직전 결과 / 재검증 결과 / 판정)를 실제로 기록하세요.
COMPLETION-RULE.md §1 — 빈 placeholder 금지.
EOF
  exit 2
fi

exit 0
