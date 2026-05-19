#!/usr/bin/env bash
# wave-fanout-guard.sh
# PreToolUse Agent — wave 진입 시 ready 셋 전체를 같은 메시지에 병렬 스폰하는지 검사.
#
# /feature 스펙 Step 2:
#   "한 wave 안의 모든 ready 티켓은 반드시 하나의 어시스턴트 메시지에 병렬 스폰한다.
#    여러 메시지에 나눠 스폰하는 것은 직렬화로 간주, 금지."
#
# 동작 (soft warning):
#   .feature-pipeline-state.json 에 ready 셋이 N>1 이고 inFlight 가 비어있는데
#   Agent tool_use 가 호출되면 stderr 로 경고 출력. (차단은 안 함 — false negative 위험.)
#
# 종료 코드: 0 (always allow, advisory only)

set -u

INPUT=$(cat 2>/dev/null || echo "")
[ -z "$INPUT" ] && exit 0

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
STATE_FILE="${PROJECT_DIR}/.feature-pipeline-state.json"

if [ ! -f "$STATE_FILE" ]; then
  exit 0
fi

# step 이 IMPLEMENTING 일 때만 활성
STATE_INFO=$(python3 -c "
import json
try:
    with open('${STATE_FILE}') as f:
        d = json.load(f)
    step = d.get('step', '')
    ready = d.get('ready', []) or []
    inflight = d.get('inFlight', []) or []
    print(f'{step}|{len(ready)}|{len(inflight)}|{\",\".join(ready)}')
except Exception:
    print('|0|0|')
" 2>/dev/null || echo "|0|0|")

STEP=$(echo "$STATE_INFO" | cut -d'|' -f1)
READY_COUNT=$(echo "$STATE_INFO" | cut -d'|' -f2)
INFLIGHT_COUNT=$(echo "$STATE_INFO" | cut -d'|' -f3)
READY_LIST=$(echo "$STATE_INFO" | cut -d'|' -f4)

if [ "$STEP" != "APPROVED" ] && [ "$STEP" != "IMPLEMENTING" ]; then
  exit 0
fi

# wave 진입 시점: inFlight 비어있고 ready ≥ 2
if [ "$INFLIGHT_COUNT" -eq 0 ] && [ "$READY_COUNT" -ge 2 ]; then
  cat >&2 <<EOF
⚠️  WAVE FAN-OUT 경고

현재 ready 셋: ${READY_COUNT}건 (${READY_LIST})
inFlight: 0건

/feature 스펙 Step 2 (Wave 스케줄러):
  "한 wave 안의 모든 ready 티켓은 반드시 하나의 어시스턴트 메시지에 병렬 스폰.
   여러 메시지에 나눠 스폰하는 것은 직렬화로 간주, 금지."

이 메시지에 ${READY_COUNT}개의 Agent tool_use 를 모두 포함했는지 확인하세요.
1개만 스폰하고 다음 메시지에서 나머지를 스폰하면 직렬화 위반입니다.
EOF
fi

exit 0
