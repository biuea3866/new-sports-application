#!/usr/bin/env bash
#
# scripts/deploy.sh <env> <image_tag>
#
# 근거: TDD.md "인터페이스 시그니처 — 배포/롤백 스크립트 계약", "실패 경로·동시성·멱등"
#
# 동작:
#   1. env(dev|prod) 인자 검증 — 그 외 즉시 거부(exit 1)
#   2. .env.<env>의 DOCKER_TAG 키만 image_tag로 치환 (다른 키는 보존)
#   3. docker compose -p sports-<env> -f docker-compose.yml -f docker-compose.<env>.yml \
#        --env-file .env.<env> up -d
#   4. http://localhost:<port>/actuator/health 폴링(최대 5분, 5초 간격)
#      HTTP 200 & body에 "status":"UP" 포함 시 exit 0, 5분 초과 시 exit 1
#      포트는 env별로 다르다 — dev: 8080, prod: 18080 (TDD Component Diagram 기준)
#
# 멱등: docker compose up -d는 desired state(이미지 태그 포함)가 현재 상태와 같으면
#       컨테이너를 재생성하지 않고 no-op으로 수렴한다(compose 자체 보장). 같은 tag로
#       반복 호출해도 안전하다. 동시 배포 직렬화는 워크플로 레벨 concurrency 그룹이
#       담당하며(TDD "동시 배포" 행 참조) 이 스크립트는 무상태로 유지한다.
#
# 이 스크립트는 rollback.sh에서 source되어 공통 함수(deploy 등)를 재사용한다.
# source될 때는 아래 BASH_SOURCE 가드가 main 실행을 막고 함수만 로드한다.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

HEALTH_POLL_MAX_SECONDS=300
HEALTH_POLL_INTERVAL_SECONDS=5

# env(dev|prod) 검증 — 그 외 인자는 어떤 side effect도 발생하기 전에 거부한다.
validate_env() {
  local env="$1"
  case "$env" in
    dev|prod) ;;
    *)
      echo "error: env must be 'dev' or 'prod', got '${env}'" >&2
      return 1
      ;;
  esac
}

# health 폴링 대상 포트 — TDD Component Diagram: dev backend :8080, prod backend :18080
health_port_for_env() {
  local env="$1"
  case "$env" in
    dev) echo 8080 ;;
    prod) echo 18080 ;;
    *)
      echo "error: unknown env '${env}' for health port resolution" >&2
      return 1
      ;;
  esac
}

# sed 치환 문자열 안의 '/' '&' '\'를 이스케이프한다 (image_tag에 슬래시가 섞여도 안전하게).
escape_sed_replacement() {
  printf '%s' "$1" | sed -e 's/[\/&\\]/\\&/g'
}

# .env.<env>의 DOCKER_TAG 키만 치환하고 다른 키는 보존한다.
# DOCKER_TAG 키가 없으면 파일 끝에 추가한다.
replace_docker_tag() {
  local env_file="$1"
  local image_tag="$2"

  if [[ ! -f "$env_file" ]]; then
    echo "error: env file not found: ${env_file}" >&2
    return 1
  fi

  local escaped_tag
  escaped_tag="$(escape_sed_replacement "$image_tag")"

  local tmp_file
  tmp_file="$(mktemp)"

  if grep -q '^DOCKER_TAG=' "$env_file"; then
    sed "s/^DOCKER_TAG=.*/DOCKER_TAG=${escaped_tag}/" "$env_file" > "$tmp_file"
  else
    cp "$env_file" "$tmp_file"
    printf 'DOCKER_TAG=%s\n' "$image_tag" >> "$tmp_file"
  fi

  mv "$tmp_file" "$env_file"
}

# docker compose up -d — override 파일명은 계약(docker-compose.<env>.yml)으로만 참조한다.
# 파일 존재는 런타임 요건(INFRA-05/06이 아직 만들지 않았을 수 있음).
compose_up() {
  local env="$1"
  local env_file="$2"
  local override_file="${REPO_ROOT}/docker-compose.${env}.yml"

  if [[ ! -f "${REPO_ROOT}/docker-compose.yml" ]]; then
    echo "error: base compose file not found: ${REPO_ROOT}/docker-compose.yml" >&2
    return 1
  fi

  if [[ ! -f "$override_file" ]]; then
    echo "error: override compose file not found: ${override_file}" >&2
    return 1
  fi

  docker compose \
    -p "sports-${env}" \
    -f "${REPO_ROOT}/docker-compose.yml" \
    -f "$override_file" \
    --env-file "$env_file" \
    up -d
}

# /actuator/health 폴링 — 최대 5분, 5초 간격. HTTP 200 & "status":"UP" 이면 성공.
poll_health() {
  local env="$1"
  local port
  port="$(health_port_for_env "$env")"
  local url="http://localhost:${port}/actuator/health"

  local elapsed=0
  while (( elapsed < HEALTH_POLL_MAX_SECONDS )); do
    local response http_code body
    response="$(curl -s -w '\n%{http_code}' --max-time 5 "$url" 2>/dev/null || true)"
    http_code="$(printf '%s' "$response" | tail -n1)"
    body="$(printf '%s' "$response" | sed '$d')"

    if [[ "$http_code" == "200" ]] && [[ "$body" == *'"status":"UP"'* ]]; then
      echo "health UP (env=${env}, port=${port}, elapsed=${elapsed}s)"
      return 0
    fi

    sleep "$HEALTH_POLL_INTERVAL_SECONDS"
    elapsed=$(( elapsed + HEALTH_POLL_INTERVAL_SECONDS ))
  done

  echo "error: health check timed out after ${HEALTH_POLL_MAX_SECONDS}s (env=${env}, port=${port})" >&2
  return 1
}

# 배포 본체 — rollback.sh가 이 함수를 그대로 재사용한다(이전 태그를 image_tag로 넘기면 롤백과 동형).
deploy() {
  local env="$1"
  local image_tag="$2"

  validate_env "$env"

  local env_file="${REPO_ROOT}/.env.${env}"
  replace_docker_tag "$env_file" "$image_tag"
  compose_up "$env" "$env_file"
  poll_health "$env"
}

# 직접 실행될 때만 main 동작 — rollback.sh가 source할 때는 함수만 로드하고 여기는 건너뛴다.
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  if [[ $# -ne 2 ]]; then
    echo "usage: $(basename "$0") <env: dev|prod> <image_tag>" >&2
    exit 1
  fi
  deploy "$1" "$2"
fi
