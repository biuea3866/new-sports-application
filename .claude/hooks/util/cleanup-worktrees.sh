#!/usr/bin/env bash
# cleanup-worktrees.sh
# 안전한 워크트리 자동정리 — "되돌릴 수 없는 손실이 0" 인 것만 제거.
#
# 제거 대상 조건(모두 충족해야 제거):
#   1. 메인 워크트리가 아님
#   2. locked 가 아님 (locked 워크트리는 작업자가 의도적으로 보호한 것)
#   3. uncommitted 변경 없음 (git status --porcelain 비어있음, untracked 포함)
#   4. 브랜치가 origin/dev 에 병합됨 (HEAD 가 origin/dev 의 조상)
#      — 또는 detached/제네릭 worktree-agent-* 이며 origin/dev 대비 고유 커밋 0개
#
# 위 조건을 하나라도 어기면 보존하고 사유를 출력한다.
# 미커밋·미머지·locked 워크트리는 절대 건드리지 않는다 → 작업물 유실 없음.
#
# 사용법:
#   cleanup-worktrees.sh            # dry-run (제거 대상만 보고, 실제 제거 안 함)
#   cleanup-worktrees.sh --execute  # 실제 제거
#   cleanup-worktrees.sh --auto     # 조용한 모드 (SessionStart 용, 안전한 것만 제거)
#
# 종료 코드: 항상 0 (정리는 실패해도 세션을 막지 않는다)

set -u

MODE="dryrun"
case "${1:-}" in
  --execute) MODE="execute" ;;
  --auto)    MODE="auto" ;;
  "")        MODE="dryrun" ;;
  *)         echo "unknown arg: $1" >&2; exit 0 ;;
esac

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-/Users/biuea/sports-application}"
cd "$PROJECT_DIR" 2>/dev/null || exit 0

# git 저장소가 아니면 종료
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

log() { [ "$MODE" != "auto" ] && echo "$@"; }

# origin/dev 기준선 확보 (없으면 머지 판정 불가 → 아무 것도 제거하지 않음)
if ! git rev-parse --verify --quiet origin/dev >/dev/null 2>&1; then
  log "origin/dev 를 찾을 수 없어 머지 판정 불가 — 정리를 건너뜁니다. (git fetch origin dev 후 재시도)"
  exit 0
fi
DEV_REF="origin/dev"

# 메타데이터 정리 (삭제된 디렉토리의 admin 엔트리 제거) — 안전
git worktree prune 2>/dev/null || true

MAIN_WT="$(git rev-parse --show-toplevel 2>/dev/null)"

REMOVED=0
KEPT=0

log ""
log "=== 워크트리 정리 (${MODE}) | 기준: ${DEV_REF} ==="
log ""

# porcelain 으로 워크트리 목록 파싱
CUR_PATH=""; CUR_BRANCH=""; CUR_LOCKED=0; CUR_DETACHED=0; CUR_HEAD=""
process_wt() {
  local path="$1" branch="$2" locked="$3" detached="$4" head="$5"
  [ -z "$path" ] && return
  # 1) 메인 제외
  if [ "$path" = "$MAIN_WT" ]; then return; fi
  local name; name="$(basename "$path")"

  # 2) locked 제외
  if [ "$locked" -eq 1 ]; then
    log "  KEEP  ${name} — locked (보호됨)"
    KEPT=$((KEPT+1)); return
  fi

  # 디렉토리 실재 확인
  if [ ! -d "$path" ]; then
    log "  KEEP  ${name} — 디렉토리 없음 (prune 대상, 수동 확인)"
    KEPT=$((KEPT+1)); return
  fi

  # 3) uncommitted 변경 (untracked 포함) 검사
  local dirty
  dirty="$(git -C "$path" status --porcelain 2>/dev/null)"
  if [ -n "$dirty" ]; then
    local n; n="$(printf '%s\n' "$dirty" | wc -l | tr -d ' ')"
    log "  KEEP  ${name} — 미커밋/미추적 변경 ${n}건 (작업물 보호)"
    KEPT=$((KEPT+1)); return
  fi

  # 4) origin/dev 에 병합되었는지: HEAD 가 dev 의 조상인가
  if git -C "$path" merge-base --is-ancestor HEAD "$DEV_REF" 2>/dev/null; then
    : # 병합됨 → 제거 가능
  else
    local ahead; ahead="$(git -C "$path" rev-list --count "${DEV_REF}..HEAD" 2>/dev/null || echo '?')"
    log "  KEEP  ${name} [${branch:-detached}] — dev 미병합 (고유 커밋 ${ahead}개, 미완료 작업)"
    KEPT=$((KEPT+1)); return
  fi

  # 제거 대상
  if [ "$MODE" = "dryrun" ]; then
    log "  WOULD-REMOVE  ${name} [${branch:-detached}] — dev 병합 완료 + clean"
    REMOVED=$((REMOVED+1))
  else
    if git worktree remove "$path" 2>/dev/null; then
      log "  REMOVED  ${name} [${branch:-detached}]"
      REMOVED=$((REMOVED+1))
    else
      log "  KEEP  ${name} — git worktree remove 실패 (수동 확인 필요)"
      KEPT=$((KEPT+1))
    fi
  fi
}

while IFS= read -r line; do
  case "$line" in
    "worktree "*)
      # 이전 엔트리 처리
      process_wt "$CUR_PATH" "$CUR_BRANCH" "$CUR_LOCKED" "$CUR_DETACHED" "$CUR_HEAD"
      CUR_PATH="${line#worktree }"; CUR_BRANCH=""; CUR_LOCKED=0; CUR_DETACHED=0; CUR_HEAD=""
      ;;
    "HEAD "*)    CUR_HEAD="${line#HEAD }" ;;
    "branch "*)  CUR_BRANCH="${line#branch refs/heads/}" ;;
    "detached")  CUR_DETACHED=1 ;;
    "locked"*)   CUR_LOCKED=1 ;;
    "")          ;; # 블록 구분 빈 줄
  esac
done < <(git worktree list --porcelain 2>/dev/null)
# 마지막 엔트리 처리
process_wt "$CUR_PATH" "$CUR_BRANCH" "$CUR_LOCKED" "$CUR_DETACHED" "$CUR_HEAD"

log ""
if [ "$MODE" = "dryrun" ]; then
  log "=== 요약: 제거 가능 ${REMOVED}개 / 보존 ${KEPT}개 ==="
  log "실제 제거하려면: bash $0 --execute"
else
  log "=== 요약: 제거 ${REMOVED}개 / 보존 ${KEPT}개 ==="
fi

# auto 모드에서 SessionStart 컨텍스트로 한 줄 요약 제공
if [ "$MODE" = "auto" ] && [ "$REMOVED" -gt 0 ]; then
  echo "[worktree-cleanup] 병합+clean 워크트리 ${REMOVED}개 자동 제거 (보존 ${KEPT}개)."
fi

exit 0
