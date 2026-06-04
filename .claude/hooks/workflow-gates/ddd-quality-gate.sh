#!/usr/bin/env bash
# ddd-quality-gate.sh
# PreToolUse(Bash) — `git push` 시 변경된 Kotlin 파일을 7대 품질 기준 중
# "결정적으로 검사 가능한" 두 가지 고신뢰 위반만 잡아 push 를 차단한다.
#
#   (A) OSIV 위반 — @Transactional 메서드가 JPA @Entity 를 반환
#       → be-code-convention "@Transactional 메서드는 DTO 반환" 위반.
#   (B) 고아(orphan) 신호 — *Repository 에 정의된 softDelete*By*/softDeleteAll*
#       자식 전파 메서드가 src/main 어디에서도 호출되지 않음(dead = 고아 신호).
#
# 주관적 기준(OO/DDD 전반/클린코드/커버리지/동시성)은 code-reviewer·pr-reviewer
# 에이전트 체크리스트가 담당한다. 이 게이트는 기계적으로 확실한 것만 deny.
#
# 비-push Bash 명령은 즉시 통과. 종료코드: 0(allow), JSON deny.

set -u

emit_allow() {
  printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow","permissionDecisionReason":"%s"}}\n' "$1"
  exit 0
}
emit_deny() {
  printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"%s"}}\n' "$1"
  exit 0
}

INPUT=$(cat 2>/dev/null || echo "")
[ -z "$INPUT" ] && exit 0

CMD=$(printf '%s' "$INPUT" | python3 -c "
import json,sys
try: print(json.load(sys.stdin).get('tool_input',{}).get('command','') or '')
except Exception: print('')
" 2>/dev/null || echo "")

# git push 가 아닌 Bash 는 검사 대상 아님 → 통과
echo "$CMD" | grep -Eq '\bgit\b.*\bpush\b' || exit 0

REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null)
[ -z "$REPO_ROOT" ] && exit 0
cd "$REPO_ROOT" || exit 0

BRANCH=$(git branch --show-current 2>/dev/null)
case "$BRANCH" in ""|dev|main|master) emit_allow "base 브랜치 - DDD 게이트 스킵" ;; esac

BASE=""
for CAND in origin/dev origin/main origin/master; do
  git rev-parse --verify "$CAND" >/dev/null 2>&1 && { BASE="$CAND"; break; }
done
[ -z "$BASE" ] && emit_allow "base 미발견 - DDD 게이트 스킵"

FINDINGS=$(BASE="$BASE" REPO_ROOT="$REPO_ROOT" python3 <<'PY'
import os, re, subprocess

base = os.environ["BASE"]; root = os.environ["REPO_ROOT"]

def sh(*a):
    return subprocess.run(a, cwd=root, capture_output=True, text=True).stdout

# 변경된 src/main 코틀린 파일
changed = [f for f in sh("git", "diff", "--name-only", f"{base}...HEAD").splitlines()
           if f.endswith(".kt") and "/src/main/" in f]

# 전체 @Entity 클래스명 집합 (도메인 Entity = JPA Entity 단일 모델)
entity_names = set()
for line in sh("git", "grep", "-l", "@Entity", "--", "*.kt").splitlines():
    p = os.path.join(root, line)
    try:
        txt = open(p, encoding="utf-8").read()
    except Exception:
        continue
    if "@Entity" not in txt:
        continue
    for m in re.finditer(r'class\s+([A-Z]\w+)', txt):
        entity_names.add(m.group(1))

findings = []

# (A) @Transactional + Entity 반환
fun_re = re.compile(r'fun\s+\w+\s*\([^)]*\)\s*:\s*([A-Za-z0-9_<>,\s\?]+)')
for f in changed:
    if "/domain/" not in f and "/application/" not in f:
        continue
    try:
        lines = open(os.path.join(root, f), encoding="utf-8").read().splitlines()
    except Exception:
        continue
    pending_tx = False
    for i, ln in enumerate(lines):
        s = ln.strip()
        if s.startswith("@Transactional"):
            pending_tx = True
            continue
        if pending_tx and "fun " in s:
            m = fun_re.search(s)
            if m:
                ret = m.group(1)
                # 반환 타입의 첫 식별자 추출 (제네릭/널 제거)
                head = re.split(r'[<\?\s]', ret.strip())[0]
                if head in entity_names:
                    findings.append(f"[OSIV] {f}:{i+1} @Transactional 메서드가 Entity '{head}' 반환 → DTO 로 변경")
            pending_tx = False
        elif pending_tx and s and not s.startswith("@"):
            pending_tx = False

# (B) dead 자식 전파 메서드 (고아 신호)
prop_decl = re.compile(r'fun\s+(softDelete(?:All)?By\w+|softDeleteAll\w*)\s*\(')
for f in changed:
    if "Repository" not in os.path.basename(f):
        continue
    try:
        txt = open(os.path.join(root, f), encoding="utf-8").read()
    except Exception:
        continue
    for m in prop_decl.finditer(txt):
        name = m.group(1)
        # src/main 전체에서 호출(선언 외) 존재 여부
        hits = sh("git", "grep", "-n", f"\\.{name}(", "--", "*.kt").strip()
        if not hits:
            findings.append(f"[ORPHAN] {f} 의 자식 전파 메서드 '{name}' 가 호출되지 않음(dead) → 루트 종료 경로에서 호출하거나 제거")

print("\n".join(findings))
PY
)

if [ -n "${FINDINGS//[$'\n\t ']/}" ]; then
  MSG=$(printf '%s' "$FINDINGS" | tr '\n' ';' | sed 's/"/\\"/g')
  emit_deny "DDD/품질 게이트 위반(고신뢰) — 수정 후 push: ${MSG}"
fi

emit_allow "DDD/품질 게이트 통과(OSIV·고아 결정적 검사)"
