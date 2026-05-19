#!/usr/bin/env bash
# pr-review-tracker.sh
# PostToolUse Agent — pr-reviewer 서브에이전트 호출을 감지해 PR 번호 기록.
#
# 트리거: tool_input.subagent_type 이 pr-reviewer 또는 *reviewer*
# 동작:
#   1. prompt 본문에서 PR 번호 추출 ("PR #N" 또는 pulls/N URL)
#   2. .claude/cache/pr-reviews.json 갱신 — reviewedPrs[PR번호] = {at, agentResult}
#
# 갱신된 기록은 pr-review-gate.sh 가 `gh pr merge` 시 검증.
# 종료 코드: 0 (observation-only, 차단 안 함)

set -u

INPUT=$(cat 2>/dev/null || echo "")
[ -z "$INPUT" ] && exit 0

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
CACHE_DIR="${PROJECT_DIR}/.claude/cache"
REVIEW_LOG="${CACHE_DIR}/pr-reviews.json"

mkdir -p "$CACHE_DIR"

REVIEW_LOG="$REVIEW_LOG" HOOK_INPUT="$INPUT" python3 <<'PYEOF'
import json, re, sys, os, datetime

# stdin/heredoc 이스케이프 취약성 회피 — 환경변수로 원본 JSON 전달
try:
    data = json.loads(os.environ.get('HOOK_INPUT', '') or '{}')
except Exception:
    sys.exit(0)

tool_input = data.get('tool_input', {})
subagent_type = (tool_input.get('subagent_type') or '').lower()

# pr-reviewer 또는 *reviewer* 패턴
if 'reviewer' not in subagent_type and 'review-pr' not in subagent_type:
    sys.exit(0)

prompt = tool_input.get('prompt', '') or ''

# PR 번호 추출: "PR #N", "pulls/N", "pull/N"
pr_nums = set()
for m in re.finditer(r'PR\s*#(\d+)', prompt):
    pr_nums.add(m.group(1))
for m in re.finditer(r'/pull[s]?/(\d+)', prompt):
    pr_nums.add(m.group(1))
for m in re.finditer(r'\bPR\s+(\d+)\b', prompt, re.IGNORECASE):
    pr_nums.add(m.group(1))

if not pr_nums:
    sys.exit(0)

review_log = os.environ['REVIEW_LOG']
log_data = {}
if os.path.exists(review_log):
    try:
        with open(review_log) as f:
            log_data = json.load(f)
    except Exception:
        log_data = {}

if 'reviewedPrs' not in log_data:
    log_data['reviewedPrs'] = {}

now_iso = datetime.datetime.utcnow().isoformat() + 'Z'

# tool_response 에서 verdict 추출 시도 (PostToolUse 단계에서는 가능)
tool_response = data.get('tool_response', {})
result_text = ''
if isinstance(tool_response, dict):
    content = tool_response.get('content', '')
    if isinstance(content, list):
        result_text = '\n'.join(
            (item.get('text', '') if isinstance(item, dict) else str(item))
            for item in content
        )
    elif isinstance(content, str):
        result_text = content

verdict = ''
m = re.search(r'verdict[:\s]*\**\s*(APPROVED|REQUEST_CHANGES|COMMENT)', result_text, re.IGNORECASE)
if m:
    verdict = m.group(1).upper()

for pr_num in pr_nums:
    log_data['reviewedPrs'][pr_num] = {
        'reviewedAt': now_iso,
        'verdict': verdict or 'UNKNOWN',
    }

with open(review_log, 'w') as f:
    json.dump(log_data, f, indent=2)
PYEOF

exit 0
