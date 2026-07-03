# Makefile — 옵저버빌리티 스택 통합 오케스트레이션 (INFRA-05 단독 소유)
#
# 근거: TDD.md "Release Scenario"·"Testing Plan", ADR-004(docker-compose 파일 분리 전략).
# 역할: 분리된 compose 파일(base + ⑧ 데이터스토어 + ⑤ 관측 백엔드 + ⑤ 관측 에이전트)을
#       `docker compose -f` 로 병합 기동/정지/로그 조회하는 단일 진입점.
#
# 파일 소유 규약(ADR-004):
#   - docker-compose.yml                          : 공용 base(backend·데이터스토어·mock) — ⑧/공용 소유
#   - docker-compose.dev.yml / prod.yml           : 환경 override(호스트 포트·APP_ENV) — ⑧ 소유
#   - docker-compose.observability.yml            : 관측 백엔드(Collector·Prom·Tempo·Loki·Grafana) — ⑤ 소유
#   - docker-compose.observability-agents.yml     : exporter 3종 + Kafka UI — ⑤ 소유(INFRA-01)
#   이 Makefile은 실행 편의일 뿐 — 개별 `-f ... down` 으로 스택별 롤백 가능(ADR-004).
#
# 사용 예:
#   make observability-up        # 로컬(기본) 전체 스택 병합 기동
#   make observability-down      # 로컬 전체 스택 정지
#   make observability-logs      # 관측 백엔드 로그 팔로우
#   make observability-config    # 병합 config 유효성 검증(exit code)
#   make observability-dev       # dev 네임스페이스(-p sports-dev) 기동
#   make observability-prod      # prod 네임스페이스(-p sports-prod) 기동

# ---- compose 파일 레이어 ----
BASE            := -f docker-compose.yml
OBS             := -f docker-compose.observability.yml
AGENTS          := -f docker-compose.observability-agents.yml
DEV_OVERRIDE    := -f docker-compose.dev.yml
PROD_OVERRIDE   := -f docker-compose.prod.yml

# 로컬(기본): base + 관측 백엔드 + 관측 에이전트. docker compose가 루트 .env 를 자동 로드한다.
LOCAL_STACK     := $(BASE) $(OBS) $(AGENTS)

# dev/prod: 환경 override 를 끼워 병합. --env-file(.env.dev/.env.prod)은 ⑧ 소유 — 여기선 참조만.
DEV_STACK       := $(BASE) $(DEV_OVERRIDE) $(OBS) $(AGENTS)
PROD_STACK      := $(BASE) $(PROD_OVERRIDE) $(OBS) $(AGENTS)

.PHONY: help \
        observability-up observability-down observability-logs observability-ps observability-config \
        observability-config-dev observability-config-prod \
        observability-dev observability-down-dev \
        observability-prod observability-down-prod

help: ## 사용 가능한 타겟 목록
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-26s\033[0m %s\n", $$1, $$2}'

# =====================================================================
# 로컬(기본) — 전체 관측 스택 병합
# =====================================================================
observability-up: ## 로컬 전체 스택(base+관측 백엔드+에이전트) 병합 기동
	docker compose $(LOCAL_STACK) up -d

observability-down: ## 로컬 전체 스택 정지·컨테이너 제거(볼륨 유지)
	docker compose $(LOCAL_STACK) down

observability-logs: ## 관측 백엔드 로그 팔로우(Grafana·Prometheus·Tempo·Loki·Collector)
	docker compose $(LOCAL_STACK) logs -f grafana prometheus tempo loki otel-collector

observability-ps: ## 전체 스택 컨테이너 상태 조회
	docker compose $(LOCAL_STACK) ps

observability-config: ## 병합 config 유효성 검증(구조 오류 시 non-zero exit)
	docker compose $(LOCAL_STACK) config -q
	@echo "OK: local merged compose config is valid"

observability-config-dev: ## dev 병합 config 유효성 검증
	docker compose $(DEV_STACK) config -q
	@echo "OK: dev merged compose config is valid"

observability-config-prod: ## prod 병합 config 유효성 검증
	docker compose $(PROD_STACK) config -q
	@echo "OK: prod merged compose config is valid"

# =====================================================================
# dev — sports-dev 네임스페이스(호스트 포트 노출·APP_ENV=dev)
#   .env.dev 는 ⑧ 소유. 없으면 배포 파이프라인(⑧) 완료 후 실행.
# =====================================================================
observability-dev: ## dev 네임스페이스 기동(-p sports-dev --env-file .env.dev)
	docker compose -p sports-dev $(DEV_STACK) --env-file .env.dev up -d

observability-down-dev: ## dev 네임스페이스 정지
	docker compose -p sports-dev $(DEV_STACK) --env-file .env.dev down

# =====================================================================
# prod — sports-prod 네임스페이스(데이터스토어 호스트 포트 미노출)
#   .env.prod(DOCKER_TAG 등)는 ⑧ 소유. scripts/deploy.sh 로 태그 핀 배포.
# =====================================================================
observability-prod: ## prod 네임스페이스 기동(-p sports-prod --env-file .env.prod)
	docker compose -p sports-prod $(PROD_STACK) --env-file .env.prod up -d

observability-down-prod: ## prod 네임스페이스 정지
	docker compose -p sports-prod $(PROD_STACK) --env-file .env.prod down
