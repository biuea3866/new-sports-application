#!/usr/bin/env bash
# qa/e2e/wait-for-healthy.sh
# docker-compose.qa.yml의 모든 서비스가 healthy 상태가 될 때까지 대기.
# 종료 코드: 0(전부 healthy), 1(타임아웃 또는 서비스 실패)

set -euo pipefail

COMPOSE_FILE="$(dirname "$0")/docker-compose.qa.yml"
TIMEOUT_SEC="${QA_WAIT_TIMEOUT:-120}"
INTERVAL_SEC=3

SERVICES=(qa-mysql qa-mongodb qa-redis qa-zookeeper qa-kafka)

echo "[wait-for-healthy] timeout=${TIMEOUT_SEC}s services=${SERVICES[*]}"

start=$(date +%s)
while true; do
  all_healthy=true
  for svc in "${SERVICES[@]}"; do
    status=$(docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "missing")
    if [ "$status" != "healthy" ]; then
      all_healthy=false
      echo "[wait-for-healthy] $svc: $status"
    fi
  done

  if [ "$all_healthy" = true ]; then
    echo "[wait-for-healthy] all healthy"
    exit 0
  fi

  elapsed=$(( $(date +%s) - start ))
  if [ "$elapsed" -ge "$TIMEOUT_SEC" ]; then
    echo "[wait-for-healthy] TIMEOUT after ${elapsed}s" >&2
    docker-compose -f "$COMPOSE_FILE" ps >&2
    exit 1
  fi
  sleep "$INTERVAL_SEC"
done
