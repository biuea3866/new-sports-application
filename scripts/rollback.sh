#!/usr/bin/env bash
#
# scripts/rollback.sh <env> <previous_image_tag>
#
# 근거: TDD.md "인터페이스 시그니처 — 배포/롤백 스크립트 계약", ADR-004 (롤백 이미지 태그 핀)
#
# 동작: deploy.sh와 동형이다 — .env.<env>의 DOCKER_TAG를 previous_image_tag로 치환 →
#       up -d → health 폴링(최대 5분). ADR-004: 롤백 = 이전 태그 재배포이며
#       DB 스냅샷 롤백은 하지 않는다(본 과제 DB 스키마 변경 0 전제).
#       목표 5분 내 복구(NFR).
#
# 공통 함수 재사용: deploy.sh를 source해 validate_env/replace_docker_tag/compose_up/
# poll_health/deploy를 그대로 사용한다. deploy.sh는 BASH_SOURCE 가드로 source 시
# main을 실행하지 않고 함수만 로드하므로 안전하다.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=./deploy.sh
source "${SCRIPT_DIR}/deploy.sh"

rollback() {
  local env="$1"
  local previous_image_tag="$2"

  validate_env "$env"

  echo "rolling back env=${env} to image_tag=${previous_image_tag}"
  deploy "$env" "$previous_image_tag"
}

if [[ $# -ne 2 ]]; then
  echo "usage: $(basename "$0") <env: dev|prod> <previous_image_tag>" >&2
  exit 1
fi

rollback "$1" "$2"
