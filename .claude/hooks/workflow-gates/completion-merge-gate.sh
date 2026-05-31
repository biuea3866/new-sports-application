#!/usr/bin/env bash
# completion-merge-gate.sh
# Stop + SubagentStop — "작업 끝났다" 면서 작업물이 로컬/피쳐 브랜치에만 남아
# dev 까지 머지되지 않은 채 대화를 종료하려는 패턴을 차단(1회 리마인드).
#
# 배경: 작업자/서브에이전트가 구현을 마쳤다고 보고하지만 실제로는
#   (a) 미커밋 변경이 워킹트리에 남아있거나
#   (b) 커밋은 했지만 원격에 push 안 됐거나
#   (c) feature 브랜치에만 있고 dev 에 머지되지 않은
#   상태로 끝나는 경우가 잦다. "작업 마무리 = dev 머지" 원칙을 강제한다.
#
# 동작: 현재 작업 디렉토리(메인 세션=세션 cwd, 서브에이전트=자기 워크트리)의
#   git 상태를 검사해 위 3개 중 하나라도 있으면 exit 2 로 종료를 막고 사유를
#   모델에게 전달한다. stop_hook_active 가 true 면(이미 1회 발화) 재차단하지
#   않는다 → 무한 루프 방지, 단발성 리마인드.
#
# 머지 검사(c)는 작업 브랜치(feat/* · fix/* · feature/* · docs/* · chore/* ·
# refactor/*)에 적용한다. dev · main · 파이프라인 베이스 브랜치는 머지 대상이
# 아니므로 (a)(b)만 검사.
#
# 종료 코드: 0(allow stop), 2(block stop + 사유 전달)

set -u

INPUT=$(cat 2>/dev/null || echo "")

# stop_hook_active 면 이미 1회 막았으므로 통과 (무한 루프 방지)
ACTIVE=$(printf '%s' "$INPUT" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    print('1' if d.get('stop_hook_active') else '0')
except Exception:
    print('0')
" 2>/dev/null || echo "0")
[ "$ACTIVE" = "1" ] && exit 0

# git 저장소가 아니면 통과
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")"
[ -z "$BRANCH" ] && exit 0
[ "$BRANCH" = "HEAD" ] && exit 0   # detached — 판정 보류

PROBLEMS=""

# (a) 미커밋 변경 (tracked 한정 — .analysis 산출물 등 untracked 노이즈 제외)
DIRTY="$(git status --porcelain --untracked-files=no 2>/dev/null)"
if [ -n "$DIRTY" ]; then
  N="$(printf '%s\n' "$DIRTY" | wc -l | tr -d ' ')"
  PROBLEMS="${PROBLEMS}\n  - ❌ 커밋되지 않은 변경 ${N}건 (브랜치 ${BRANCH})"
fi

# (b) 미push 커밋 (upstream 대비 ahead)
UPSTREAM="$(git rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>/dev/null || echo "")"
if [ -n "$UPSTREAM" ]; then
  AHEAD="$(git rev-list --count "${UPSTREAM}..HEAD" 2>/dev/null || echo "0")"
  if [ "$AHEAD" != "0" ]; then
    PROBLEMS="${PROBLEMS}\n  - ❌ push 되지 않은 커밋 ${AHEAD}개 (${BRANCH} → ${UPSTREAM})"
  fi
else
  # upstream 미설정 — 작업 브랜치면 아직 한 번도 push 안 한 것
  case "$BRANCH" in
    feat/*|fix/*|feature/*|docs/*|chore/*|refactor/*)
      PROBLEMS="${PROBLEMS}\n  - ❌ 원격 추적 브랜치 없음 — 한 번도 push 되지 않음 (${BRANCH})"
      ;;
  esac
fi

# (c) dev 미머지 — 작업 브랜치(feat/fix/feature/docs/chore/refactor)에 적용
case "$BRANCH" in
  feat/*|fix/*|feature/*|docs/*|chore/*|refactor/*)
    if git rev-parse --verify --quiet origin/dev >/dev/null 2>&1; then
      if ! git merge-base --is-ancestor HEAD origin/dev 2>/dev/null; then
        UNIQ="$(git rev-list --count origin/dev..HEAD 2>/dev/null || echo "?")"
        PROBLEMS="${PROBLEMS}\n  - ❌ origin/dev 에 미머지 (고유 커밋 ${UNIQ}개) — PR 생성·머지 미완료"
      fi
    fi
    ;;
esac

[ -z "$PROBLEMS" ] && exit 0

WT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

{
  echo "BLOCKED(종료 보류): 작업이 dev 까지 마무리되지 않았습니다."
  echo ""
  echo "워크트리: ${WT}"
  echo "브랜치: ${BRANCH}"
  printf "%b\n" "남은 작업:${PROBLEMS}"
  echo ""
  echo "\"작업 마무리\" 의 정의는 **dev 에 머지 완료** 입니다. 다음 중 하나를 수행하세요:"
  echo "  1. 변경 커밋 → push → PR 생성 → 리뷰 → dev 머지까지 완료"
  echo "     (pr-create 스킬 사용 가능)"
  echo "  2. 지금 마무리할 수 없으면, 사용자에게 '무엇이 왜 미완료인지'를"
  echo "     명시적으로 보고하세요. '완료/끝났다' 라고 단언하지 마세요."
  echo ""
  echo "  (이 게이트는 1회만 발화합니다. 위를 처리했거나 미완료임을 보고했다면"
  echo "   다음 종료 시도는 통과합니다.)"
} >&2

exit 2
