#!/usr/bin/env bash
# qa-env-gate.sh
# PreToolUse Bash — `/qa` 파이프라인에서 Playwright E2E 실행 직전,
# FE 환경 변수(web/.env.local의 BACKEND_URL)가 설정됐는지 강제 검증한다.
#
# 배경: .env.local 없이 E2E를 돌리면 be-client.ts가 모듈 로드 시 throw →
# /portal/* SSR 500 → portal 화면 시나리오가 무더기 거짓 fail → 환경 결함을
# 코드 결함으로 오인 (DEF-002 회고).
#
# 동작:
#   - `npx playwright test` / `playwright test` 명령 감지
#   - web/.env.local 에 BACKEND_URL 항목이 있는지 확인
#   - 없으면 차단 (deny)
#
# 격리 worktree 컨텍스트는 통과.
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

# playwright test 실행 명령이 아니면 통과
echo "$CMD" | grep -qE '(npx )?playwright +test' || exit 0

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-/Users/biuea/sports-application}"

# 격리 worktree 컨텍스트는 통과
case "$PROJECT_DIR" in
  */.claude/worktrees/agent-*)
    exit 0
    ;;
esac

cd "$PROJECT_DIR" 2>/dev/null || exit 0

# web/ 디렉토리가 없으면 이 프로젝트의 QA 대상 아님 — 통과
[ -d "web" ] || exit 0

ENV_FILE="web/.env.local"

if [ ! -f "$ENV_FILE" ]; then
  cat >&2 <<EOF
BLOCKED: Playwright E2E 실행을 차단합니다 — web/.env.local 이 없습니다.

Next.js FE는 BACKEND_URL 을 모듈 로드 시점에 요구합니다.
.env.local 없이 회귀를 돌리면 /portal/* 전 페이지가 SSR 500 →
portal 화면 시나리오가 무더기 거짓 fail 합니다 (DEF-002 회고).

해결 (/qa Step 0-B):
  cat > web/.env.local <<'ENV'
  BACKEND_URL=http://localhost:8080
  NEXT_PUBLIC_APP_NAME=Sports Application
  ENV
EOF
  exit 2
fi

if ! grep -q '^BACKEND_URL=' "$ENV_FILE"; then
  cat >&2 <<EOF
BLOCKED: web/.env.local 에 BACKEND_URL 항목이 없습니다.

현재 web/.env.local:
$(sed 's/^/  /' "$ENV_FILE")

BACKEND_URL=http://localhost:8080 줄을 추가하세요 (/qa Step 0-B).
EOF
  exit 2
fi

# BACKEND_URL 값이 비어 있는지
BV=$(grep '^BACKEND_URL=' "$ENV_FILE" | head -1 | cut -d= -f2-)
if [ -z "$BV" ]; then
  echo "BLOCKED: web/.env.local 의 BACKEND_URL 값이 비어 있습니다." >&2
  exit 2
fi

exit 0
