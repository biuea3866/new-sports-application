#!/usr/bin/env bash
# qa-fe-mode-gate.sh
# PreToolUse Bash — Playwright E2E 실행 직전, FE 가 production 모드(next start)인지 검증한다.
#
# 배경: next dev 는 hydration 타이밍 비결정성으로 Playwright 가 폼 hydration 전에
# 클릭 → 네이티브 submit → 페이지 리로드 → 거짓 실패가 발생한다 (/login 회귀에서 4건 중
# 3건이 dev 모드에서 거짓 fail, production 빌드에선 4/4 통과로 확정된 사례).
#
# 동작:
#   - `npx playwright test` / `playwright test` 명령 감지
#   - `ps -ef` 에 `next dev` 프로세스가 보이면 차단
#   - `next start` 프로세스도 함께 없으면(localhost:3000 응답 자체가 없음) 차단
#   - 격리 worktree 컨텍스트는 통과 (메인 세션 책임)
#
# 회피: 정말 dev 모드로 돌려야 한다면 명령에 `--fe-mode-skip` 주석 포함
#   예: npx playwright test ...  # --fe-mode-skip 임시 디버깅
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

# playwright test 명령이 아니면 통과
echo "$CMD" | grep -qE '(npx )?playwright +test' || exit 0

# 명시적 회피 키워드
if echo "$CMD" | grep -qF '--fe-mode-skip'; then
  echo "[qa-fe-mode-gate] --fe-mode-skip 감지 — 통과 (디버깅 모드)" >&2
  exit 0
fi

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-/Users/biuea/sports-application}"

# 격리 worktree 컨텍스트는 통과 — 메인 세션 책임
case "$PROJECT_DIR" in
  */.claude/worktrees/agent-*)
    exit 0
    ;;
esac

# next dev 프로세스 감지
DEV_PROCS=$(ps -ef | grep -E "next dev|next-server.*dev" | grep -v grep | head -3 || true)

if [ -n "$DEV_PROCS" ]; then
  cat >&2 <<EOF
BLOCKED: FE 가 next dev (개발 모드)로 떠 있어 Playwright E2E 실행을 차단합니다.

감지된 dev 프로세스:
$(echo "$DEV_PROCS" | awk '{print "  " $2, $8, $9, $10, $11}')

next dev 는 hydration 타이밍 비결정성으로 Playwright E2E 가 거짓 실패합니다
— 폼이 hydration 전에 제출돼 네이티브 submit 으로 페이지가 리로드됩니다
(/login 회귀에서 4건 중 3건 거짓 fail → production 빌드에서 4/4 통과 확정).

해결 (/qa Step 0-C):
  pkill -9 -f "next dev"
  cd web && npx next build && nohup npx next start -p 3000 &

회피 (디버깅 한정): 명령에 \`# --fe-mode-skip\` 주석 추가.
EOF
  exit 2
fi

# next start 도 안 떠있으면 FE 자체가 없음 — 별도 가드 (qa-env-gate 가 .env.local 검사하지만
# next start 프로세스까지는 안 봄)
START_PROCS=$(ps -ef | grep -E "next start|next-server.*(?<!dev)" | grep -v grep | grep -v "next dev" | head -3 || true)

if [ -z "$START_PROCS" ]; then
  # FE 자체가 안 떠있으면 — Playwright 가 어차피 connection refused. 경고만, 차단은 안 함
  # (Playwright config 의 webServer 가 next start 를 자동으로 띄울 수도 있음)
  echo "[qa-fe-mode-gate] WARN: next dev/start 프로세스 미감지. Playwright config.webServer 가 띄우거나 connection refused 가능." >&2
fi

exit 0
