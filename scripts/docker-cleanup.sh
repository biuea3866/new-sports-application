#!/usr/bin/env bash
#
# scripts/docker-cleanup.sh <env: dev|prod> [--dry-run]
#
# 근거: requirement.md "Docker 운영 위생"
#
# 동작:
#   1. 이미지 태그 보존 — sports-backend 최신 KEEP_TAGS(=3)개 SHA 태그만 보존, 나머지 rmi
#   2. dangling 이미지 prune (untagged만)
#   3. dangling 볼륨 prune — 단 데이터스토어 named 볼륨(mysql/mongodb/redis/kafka/minio
#      데이터)은 어떤 경우에도 삭제하지 않는다(p0). prune 전 방어 가드로 dangling 목록에
#      보호 패턴 매칭 볼륨이 하나라도 있으면 prune을 중단하고 에러 종료한다.
#   4. 중단/고아 컨테이너 정리 — 대상 compose 프로젝트(sports-<env>) 컨테이너만
#
# --dry-run: 삭제 대상만 출력하고 실제 삭제는 하지 않는다.
#
# 이 스크립트는 deploy.sh의 BASH_SOURCE 가드 안(직접 실행 시)에서, deploy() 성공 이후에만
# 호출된다. rollback.sh는 deploy.sh를 source해 deploy() 함수만 재사용하므로 이 스크립트를
# 호출하는 경로에 도달하지 않는다 — 롤백 대상이 될 수 있는 이전 이미지를 이 스크립트가
# 지우는 사고를 원천 차단한다.

set -euo pipefail

# 롤백용으로 보존할 최신 SHA 태그 개수. 방금 배포된 최신 태그도 이 안에 포함되어 항상 보존된다.
readonly KEEP_TAGS=3

# 데이터스토어 named 볼륨 보호 패턴(p0) — compose가 프로젝트 접두사를 붙인 이름
# (예: sports-dev_mysql-data)도 이 패턴들 중 하나를 부분 문자열로 포함하면 보호 대상이다.
readonly PROTECTED_VOLUME_PATTERNS=(
  "mysql-data"
  "mongodb-data"
  "redis-data"
  "kafka-data"
  "minio-data"
)

DRY_RUN=0

log() {
  echo "[docker-cleanup] $*"
}

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

# 볼륨명이 보호 패턴 중 하나라도 포함하면 0(보호 대상), 아니면 1(삭제 후보)을 반환한다.
is_protected_volume() {
  local volume_name="$1"
  local pattern
  for pattern in "${PROTECTED_VOLUME_PATTERNS[@]}"; do
    if [[ "$volume_name" == *"$pattern"* ]]; then
      return 0
    fi
  done
  return 1
}

# ---- 1. 이미지 태그 보존 정책 ----
# sports-backend 이미지 중 latest/local/<none> 태그를 제외하고 생성시각 내림차순 정렬,
# 최신 KEEP_TAGS개를 보존하고 나머지는 rmi 대상으로 삼는다.
cleanup_old_image_tags() {
  log "이미지 태그 정리 시작 (KEEP_TAGS=${KEEP_TAGS})"

  if ! command -v docker >/dev/null 2>&1; then
    log "docker 커맨드를 찾을 수 없어 이미지 태그 정리를 건너뜁니다"
    return 0
  fi

  local image_lines
  image_lines="$(docker images sports-backend --format '{{.Tag}}\t{{.CreatedAt}}' 2>/dev/null || true)"

  if [[ -z "$image_lines" ]]; then
    log "sports-backend 이미지가 없어 정리할 대상이 없습니다"
    return 0
  fi

  # latest/local/<none> 태그 제외 → 생성시각 내림차순 정렬 → 태그만 추출
  local sorted_tags
  sorted_tags="$(printf '%s\n' "$image_lines" \
    | awk -F'\t' '$1 != "latest" && $1 != "local" && $1 != "<none>" { print }' \
    | sort -t$'\t' -k2 -r \
    | cut -f1)"

  if [[ -z "$sorted_tags" ]]; then
    log "보존 정책 대상 SHA 태그가 없습니다"
    return 0
  fi

  local old_tags
  old_tags="$(printf '%s\n' "$sorted_tags" | tail -n +"$((KEEP_TAGS + 1))")"

  if [[ -z "$old_tags" ]]; then
    log "보존 기준(${KEEP_TAGS}개) 이내라 삭제할 오래된 태그가 없습니다"
    return 0
  fi

  local tag
  while IFS= read -r tag; do
    [[ -z "$tag" ]] && continue
    if [[ "$DRY_RUN" -eq 1 ]]; then
      log "[dry-run] rmi 대상: sports-backend:${tag}"
    else
      log "rmi 실행: sports-backend:${tag}"
      # 사용 중인 이미지(예: 현재 실행 중인 컨테이너)는 docker가 자연스럽게 삭제를 거부한다.
      # 정리 흐름이 중단되지 않도록 실패는 무시하고 계속 진행한다.
      docker rmi "sports-backend:${tag}" || true
    fi
  done <<< "$old_tags"
}

# ---- 2. dangling 이미지 정리 ----
cleanup_dangling_images() {
  log "dangling 이미지 정리 시작"

  if [[ "$DRY_RUN" -eq 1 ]]; then
    local dangling
    dangling="$(docker images -f dangling=true -q 2>/dev/null || true)"
    if [[ -z "$dangling" ]]; then
      log "[dry-run] dangling 이미지 없음"
    else
      log "[dry-run] 삭제 후보 dangling 이미지: $(printf '%s' "$dangling" | tr '\n' ' ')"
    fi
    return 0
  fi

  docker image prune -f
}

# ---- 3. dangling 볼륨 정리 (p0 방어 다중화) ----
cleanup_dangling_volumes() {
  log "dangling 볼륨 정리 시작"

  local dangling_volumes
  dangling_volumes="$(docker volume ls -q -f dangling=true 2>/dev/null || true)"

  if [[ -z "$dangling_volumes" ]]; then
    log "dangling 볼륨 없음"
    return 0
  fi

  # prune 실행 전 방어 가드: dangling 목록에 보호 패턴 매칭 볼륨이 하나라도 있으면
  # 비정상 상황(데이터 볼륨이 dangling으로 잡힘)으로 간주해 prune 자체를 중단한다.
  local risky_volumes=()
  local volume
  while IFS= read -r volume; do
    [[ -z "$volume" ]] && continue
    if is_protected_volume "$volume"; then
      risky_volumes+=("$volume")
    fi
  done <<< "$dangling_volumes"

  if [[ "${#risky_volumes[@]}" -gt 0 ]]; then
    echo "error: dangling 볼륨 목록에 데이터스토어 보호 패턴과 매칭되는 볼륨이 발견되어 prune을 중단합니다" >&2
    local risky
    for risky in "${risky_volumes[@]}"; do
      echo "  - 위험 볼륨: ${risky}" >&2
    done
    return 1
  fi

  if [[ "$DRY_RUN" -eq 1 ]]; then
    log "[dry-run] 삭제 후보 dangling 볼륨: $(printf '%s' "$dangling_volumes" | tr '\n' ' ')"
    return 0
  fi

  # --all 은 절대 사용하지 않는다 — named 볼륨까지 삭제 대상에 포함시키는 옵션이다.
  # 인자 없는 기본 prune은 dangling(어떤 컨테이너도 참조하지 않는 anonymous 위주) 볼륨만 대상으로 한다.
  docker volume prune -f
}

# ---- 4. 중단/고아 컨테이너 정리 ----
cleanup_stopped_containers() {
  local env="$1"
  local project="sports-${env}"

  log "중단 컨테이너 정리 시작 (project=${project})"

  if [[ "$DRY_RUN" -eq 1 ]]; then
    local candidates
    candidates="$(docker container ls -a -q -f status=exited -f "label=com.docker.compose.project=${project}" 2>/dev/null || true)"
    if [[ -z "$candidates" ]]; then
      log "[dry-run] ${project} 프로젝트의 중단 컨테이너 없음"
    else
      log "[dry-run] 삭제 후보 컨테이너: $(printf '%s' "$candidates" | tr '\n' ' ')"
    fi
    return 0
  fi

  docker container prune -f --filter "label=com.docker.compose.project=${project}"
}

main() {
  if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "usage: $(basename "$0") <env: dev|prod> [--dry-run]" >&2
    exit 1
  fi

  local env="$1"
  validate_env "$env"

  if [[ $# -eq 2 ]]; then
    if [[ "$2" != "--dry-run" ]]; then
      echo "error: unknown option '$2' (expected --dry-run)" >&2
      exit 1
    fi
    DRY_RUN=1
    log "dry-run 모드 — 실제 삭제는 수행하지 않습니다"
  fi

  cleanup_old_image_tags
  cleanup_dangling_images
  cleanup_dangling_volumes
  cleanup_stopped_containers "$env"

  log "정리 완료 (env=${env}, dry-run=${DRY_RUN})"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
