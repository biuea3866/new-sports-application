#!/usr/bin/env bash
# qa-pr-defect-gate.sh
# PreToolUse Bash — `gh pr create` 시 다음을 검사:
#
# 1) PR diff에 새 화면(web/app/**)/엔드포인트(backend/**/Controller.kt) 추가
#    → qa/e2e/scenarios/ 또는 .analysis/outputs/qa/<날짜>/scenarios/e2e/ 에
#      관련 시나리오 md가 존재해야 함
#
# 2) `fix/qa-*` 브랜치(자동 결함 수정)는 .analysis/outputs/qa/.../defects/<id>-*.md
#    가 존재해야 함 — 결함 md 없는 자동 fix PR 차단
#
# 메인 워크트리에서만 동작. 격리 worktree(.claude/worktrees/agent-*)는 건너뜀
# — be-implementer/fe-implementer가 자기 격리 환경에서 PR을 만들 때는 결함 md가
# 메인 워크트리에만 있을 수 있어서 false positive 방지.
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

# gh pr create 가 아니면 통과
echo "$CMD" | grep -qE 'gh\s+pr\s+create' || exit 0

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-/Users/biuea/sports-application}"

# 격리 worktree 컨텍스트는 통과 — 결함 md가 메인에만 있을 수 있음
case "$PROJECT_DIR" in
  */.claude/worktrees/agent-*)
    exit 0
    ;;
esac

cd "$PROJECT_DIR" 2>/dev/null || exit 0

# 현재 브랜치
BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")

# Case 2: fix/qa-* 브랜치 — 결함 md 존재 검사
if echo "$BRANCH" | grep -qE '^fix/qa-'; then
  SLUG=$(echo "$BRANCH" | sed -E 's|^fix/qa-[0-9]{8}-||')
  if ! find .analysis/outputs/qa -type f -name "*${SLUG}*.md" 2>/dev/null | grep -q defects/; then
    cat >&2 <<EOF
BLOCKED: fix/qa-* 브랜치인데 대응하는 결함 md를 찾을 수 없습니다.

브랜치: $BRANCH
검색 패턴: .analysis/outputs/qa/**/defects/*${SLUG}*.md

자동 수정 PR은 반드시 qa-defect-router가 산출한 결함 md와 1:1 매핑되어야 합니다.

해결:
  - /qa 파이프라인을 통해 결함 발견 → 자동 호출 흐름 사용
  - 또는 결함 md를 .analysis/outputs/qa/{YYYYMMDD}_{topic}/defects/ 에 수동 작성
EOF
    exit 2
  fi
fi

# Case 1: 새 화면/Controller 추가 검사
# 메인 브랜치(dev) 기준 변경 파일 수집
BASE_BRANCH="${QA_BASE_BRANCH:-dev}"
git rev-parse --verify "origin/${BASE_BRANCH}" >/dev/null 2>&1 || exit 0

CHANGED=$(git diff --name-only "origin/${BASE_BRANCH}...HEAD" 2>/dev/null || echo "")
[ -z "$CHANGED" ] && exit 0

# 새 Controller 또는 새 page 추가만 검사 (수정은 통과)
NEW_CONTROLLER=$(echo "$CHANGED" | grep -E 'backend/.*Controller\.kt$' || true)
NEW_PAGE=$(echo "$CHANGED" | grep -E 'web/app/.*/(page|layout)\.(tsx|ts)$' || true)

if [ -z "$NEW_CONTROLLER" ] && [ -z "$NEW_PAGE" ]; then
  exit 0
fi

# 시나리오 md 존재 여부 — qa/e2e/scenarios 또는 .analysis/outputs/qa/.../scenarios
SCENARIO_COUNT=$(find qa/e2e/scenarios .analysis/outputs/qa -type f -name '*.md' 2>/dev/null \
  -newer .git/refs/remotes/origin/"${BASE_BRANCH}" | wc -l | tr -d ' ')

if [ "$SCENARIO_COUNT" -eq 0 ]; then
  cat >&2 <<EOF
BLOCKED: 새 화면 또는 Controller가 추가됐는데 QA 시나리오 md가 없습니다.

변경된 새 파일:
$(echo "$NEW_CONTROLLER" | sed 's/^/  - /')
$(echo "$NEW_PAGE" | sed 's/^/  - /')

해결:
  - /qa 슬래시 커맨드 실행 — qa-scenario-author가 자동 도출
  - 또는 qa/e2e/scenarios/{flow-slug}.md 를 수동 작성
    (형식: .claude/rules/qa-scenario-guide.md)

회피하려면 PR 본문에 "[skip-qa-gate]" 명시 + 그 사유를 함께 적으세요.
EOF

  # skip 키워드 검사 — PR 본문에 [skip-qa-gate] 가 있으면 통과
  if echo "$CMD" | grep -qF '[skip-qa-gate]'; then
    echo "[qa-pr-defect-gate] skip-qa-gate 감지 — 통과" >&2
    exit 0
  fi

  exit 2
fi

exit 0
