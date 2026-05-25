#!/usr/bin/env bash
# collect-baseline.sh — /api/admin/* GET 요청 기준 운영자 클릭 baseline 집계
#
# 사용법:
#   LOG_SOURCE=nginx_local ./collect-baseline.sh --users user_ids.csv --start 2026-05-17 --end 2026-05-23
#   LOG_SOURCE=datadog     ./collect-baseline.sh --users user_ids.csv --start 2026-05-17 --end 2026-05-23
#
# 환경변수:
#   LOG_SOURCE           - nginx_local | datadog (필수)
#   NGINX_LOG_PATH       - nginx_local 모드 시 로그 경로 (기본: /var/log/nginx/access.log)
#   DATADOG_API_KEY      - datadog 모드 필수 (평문 커밋 금지)
#   DATADOG_APP_KEY      - datadog 모드 필수 (평문 커밋 금지)
#   DATADOG_SITE         - datadog 모드 (기본: datadoghq.com)
#   LOG_INDEX            - datadog 모드 log index (기본: main)

set -euo pipefail

# ---------- 기본값 ----------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_DIR="${SCRIPT_DIR}/results"
LOG_SOURCE="${LOG_SOURCE:-nginx_local}"
NGINX_LOG_PATH="${NGINX_LOG_PATH:-/var/log/nginx/access.log}"
DATADOG_SITE="${DATADOG_SITE:-datadoghq.com}"
LOG_INDEX="${LOG_INDEX:-main}"

USERS_INPUT=""
START_DATE=""
END_DATE=""

# ---------- 인자 파싱 ----------
usage() {
  echo "사용법: LOG_SOURCE=nginx_local|datadog ./collect-baseline.sh --users <file|-> --start YYYY-MM-DD --end YYYY-MM-DD" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --users)  USERS_INPUT="$2"; shift 2 ;;
    --start)  START_DATE="$2";  shift 2 ;;
    --end)    END_DATE="$2";    shift 2 ;;
    *)        usage ;;
  esac
done

[[ -z "${USERS_INPUT}" || -z "${START_DATE}" || -z "${END_DATE}" ]] && usage

# ---------- 입력 검증 ----------
if ! [[ "${START_DATE}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  echo "오류: --start 는 YYYY-MM-DD 형식이어야 합니다 (입력: ${START_DATE})" >&2
  exit 2
fi
if ! [[ "${END_DATE}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  echo "오류: --end 는 YYYY-MM-DD 형식이어야 합니다 (입력: ${END_DATE})" >&2
  exit 2
fi
if [[ "${START_DATE}" > "${END_DATE}" ]]; then
  echo "오류: --start (${START_DATE}) 가 --end (${END_DATE}) 보다 늦습니다" >&2
  exit 2
fi

# ---------- user_id 목록 로드 ----------
load_user_ids() {
  if [[ "${USERS_INPUT}" == "-" ]]; then
    cat
  else
    cat "${USERS_INPUT}"
  fi | tr ',' '\n' | sed 's/[[:space:]]//g' | grep -v '^$'
}

# ---------- 날짜 범위 생성 (YYYY-MM-DD 순열) ----------
date_range() {
  local start="$1"
  local end="$2"
  local current="${start}"

  while [[ "${current}" < "${end}" || "${current}" == "${end}" ]]; do
    echo "${current}"
    current=$(date -d "${current} +1 day" +%Y-%m-%d 2>/dev/null \
              || date -v+1d -j -f "%Y-%m-%d" "${current}" +%Y-%m-%d)
  done
}

# ---------- nginx_local 모드: grep 기반 집계 ----------
collect_nginx_local() {
  local user_id="$1"
  local date="$2"

  # nginx log 날짜 형식: [24/May/2026:...
  local day month year
  day=$(echo "${date}" | cut -d'-' -f3)
  month_num=$(echo "${date}" | cut -d'-' -f2)
  year=$(echo "${date}" | cut -d'-' -f1)

  # 월 숫자 → 영문 약어
  local month
  case "${month_num}" in
    01) month="Jan" ;; 02) month="Feb" ;; 03) month="Mar" ;; 04) month="Apr" ;;
    05) month="May" ;; 06) month="Jun" ;; 07) month="Jul" ;; 08) month="Aug" ;;
    09) month="Sep" ;; 10) month="Oct" ;; 11) month="Nov" ;; 12) month="Dec" ;;
    *) echo "오류: 잘못된 월 ${month_num}" >&2; return 1 ;;
  esac

  local date_pattern="${day}/${month}/${year}"

  # 로그 파일 목록 (rotated 포함)
  local log_files=()
  while IFS= read -r -d '' f; do
    log_files+=("$f")
  done < <(find "$(dirname "${NGINX_LOG_PATH}")" \
    -name "$(basename "${NGINX_LOG_PATH}")*" -print0 2>/dev/null)

  if [[ ${#log_files[@]} -eq 0 ]]; then
    echo "경고: 로그 파일을 찾을 수 없습니다: ${NGINX_LOG_PATH}" >&2
    echo "0 0"
    return
  fi

  # GET /api/admin/* 요청 + 해당 user_id + 해당 날짜 필터
  # word boundary (macOS BSD grep -P 미지원 → 명시적 공백/경계 매칭):
  #   앞: " user_id=" (공백 prefix) — related_user_id 같은 다른 필드 매칭 차단
  #   뒤: user_id=42 뒤에 숫자 안 오기 — user_id=420 차단
  local matched
  matched=$(grep -h "${date_pattern}" "${log_files[@]}" 2>/dev/null \
    | grep '"GET /api/admin/' \
    | grep " user_id=${user_id}" \
    | grep -v "user_id=${user_id}[0-9]" \
    || true)

  # 0건 매칭 시 awk NR 사용 — grep -c || echo 0 패턴은 stdout 멀티라인 손상 위험
  local total_clicks
  if [[ -z "${matched}" ]]; then
    total_clicks=0
  else
    total_clicks=$(echo "${matched}" | awk 'END {print NR}')
  fi

  # macOS BSD grep은 -P 미지원 → -oE + 후처리로 경로 추출
  local distinct_paths
  if [[ -z "${matched}" ]]; then
    distinct_paths=0
  else
    distinct_paths=$(echo "${matched}" \
      | grep -oE '"GET /api/admin/[^?"[:space:]]+' \
      | sed 's/"GET //' \
      | sort -u \
      | awk 'END {print NR}')
  fi

  echo "${total_clicks} ${distinct_paths}"
}

# ---------- datadog 모드: Logs API 집계 ----------
collect_datadog() {
  local user_id="$1"
  local date="$2"

  if [[ -z "${DATADOG_API_KEY:-}" || -z "${DATADOG_APP_KEY:-}" ]]; then
    echo "오류: DATADOG_API_KEY, DATADOG_APP_KEY 환경변수가 필요합니다." >&2
    exit 1
  fi

  local from_ts to_ts
  from_ts="${date}T00:00:00+00:00"
  to_ts="${date}T23:59:59+00:00"

  # Datadog Logs API — aggregate endpoint
  local response
  response=$(curl --silent --fail \
    -X POST "https://api.${DATADOG_SITE}/api/v2/logs/analytics/aggregate" \
    -H "DD-API-KEY: ${DATADOG_API_KEY}" \
    -H "DD-APPLICATION-KEY: ${DATADOG_APP_KEY}" \
    -H "Content-Type: application/json" \
    --data-binary @- <<EOF
{
  "compute": [
    { "aggregation": "count", "type": "total", "metric": "@http.status_code" }
  ],
  "filter": {
    "from": "${from_ts}",
    "to": "${to_ts}",
    "indexes": ["${LOG_INDEX}"],
    "query": "@http.method:GET @http.url_details.path:/api/admin/* @usr.id:${user_id}"
  },
  "group_by": [
    { "facet": "@http.url_details.path", "limit": 1000, "sort": { "aggregation": "count", "order": "desc" } }
  ]
}
EOF
  )

  local total_clicks distinct_paths
  # macOS BSD grep은 -P 미지원 → -oE + sed로 숫자 추출
  total_clicks=$(echo "${response}" | grep -oE '"count":[[:space:]]*[0-9]+' | grep -oE '[0-9]+$' | awk '{s+=$1} END {print s+0}')
  distinct_paths=$(echo "${response}" | grep -oE '"by":[[:space:]]*\{[^}]+\}' | wc -l | tr -d ' ')

  echo "${total_clicks} ${distinct_paths}"
}

# ---------- 메인 집계 ----------
main() {
  mkdir -p "${RESULTS_DIR}"

  local output_file="${RESULTS_DIR}/baseline-${START_DATE//-/}-${END_DATE//-/}.csv"

  echo "user_id,date,total_clicks,distinct_paths" > "${output_file}"

  user_ids=()
  while IFS= read -r uid; do
    user_ids+=("${uid}")
  done < <(load_user_ids)

  if [[ ${#user_ids[@]} -eq 0 ]]; then
    echo "오류: user_id 목록이 비어 있습니다." >&2
    exit 1
  fi

  dates=()
  while IFS= read -r d; do
    dates+=("${d}")
  done < <(date_range "${START_DATE}" "${END_DATE}")

  local total_rows=0

  for user_id in "${user_ids[@]}"; do
    for date in "${dates[@]}"; do
      local clicks paths

      case "${LOG_SOURCE}" in
        nginx_local)
          read -r clicks paths < <(collect_nginx_local "${user_id}" "${date}") ;;
        datadog)
          read -r clicks paths < <(collect_datadog "${user_id}" "${date}") ;;
        *)
          echo "오류: LOG_SOURCE는 nginx_local 또는 datadog 이어야 합니다. 현재값: ${LOG_SOURCE}" >&2
          exit 1 ;;
      esac

      echo "${user_id},${date},${clicks},${paths}" >> "${output_file}"
      (( total_rows++ )) || true
    done
  done

  echo "완료: ${output_file} (${total_rows}행 기록)" >&2
  echo "${output_file}"
}

main
