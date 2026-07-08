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
LB              := -f docker-compose.lb.yml
SIM             := -f docker-compose.sim.yml

# 로컬(기본): base + 관측 백엔드 + 관측 에이전트. docker compose가 루트 .env 를 자동 로드한다.
LOCAL_STACK     := $(BASE) $(OBS) $(AGENTS)

# dev/prod: 환경 override 를 끼워 병합. --env-file(.env.dev/.env.prod)은 ⑧ 소유 — 여기선 참조만.
DEV_STACK       := $(BASE) $(DEV_OVERRIDE) $(OBS) $(AGENTS)
PROD_STACK      := $(BASE) $(PROD_OVERRIDE) $(OBS) $(AGENTS)

# 시뮬레이터(⑦, INFRA-09): 관측+LB+sim을 얹는다. 설계 전제(TDD "무엇을")는 prod 대상이지만
# dev/local 변형도 검증용으로 제공한다.
LOCAL_SIM_STACK := $(LOCAL_STACK) $(LB) $(SIM)
DEV_SIM_STACK   := $(DEV_STACK) $(LB) $(SIM)
PROD_SIM_STACK  := $(PROD_STACK) $(LB) $(SIM)

.PHONY: help \
        observability-up observability-down observability-logs observability-ps observability-config \
        observability-config-dev observability-config-prod \
        observability-dev observability-down-dev \
        observability-prod observability-down-prod \
        sim-config sim-config-dev sim-config-prod \
        sim-up sim-pause sim-down sim-ps sim-logs \
        sim-up-dev sim-down-dev \
        sim-up-prod sim-down-prod

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

# =====================================================================
# 상시 트래픽 시뮬레이터 (⑦, INFRA-09) — k6-runner·reseed 제어
#   FR-7 상태 전이: 정지 -> sim-up(구동중) -> sim-pause(일시정지) -> sim-up(구동중) -> sim-down(정지)
#   graceful: k6-runner의 stop_grace_period(docker-compose.sim.yml, 45s)가 `stop`/`down`에
#             자동 적용되어 k6 gracefulStop(기본 30s) 동안 도착률이 0으로 수렴한 뒤 정지한다.
#   sim-pause 는 k6-runner만 멈추고 reseed·LB·backend·관측 스택은 유지한다(TDD 상태 전이 표).
#   sim-down 은 k6-runner+reseed만 정지하고 나머지 스택(backend·LB·관측)은 무영향으로 둔다
#   (롤백 — "-f docker-compose.sim.yml 미적용"과 동등 효과를 즉시 낸다).
# =====================================================================
sim-config: ## 로컬 시뮬 스택(base+관측+LB+sim) 병합 config 검증
	docker compose $(LOCAL_SIM_STACK) config -q
	@echo "OK: local sim merged compose config is valid"

sim-config-dev: ## dev 시뮬 스택 병합 config 검증
	docker compose $(DEV_SIM_STACK) --env-file .env.dev config -q
	@echo "OK: dev sim merged compose config is valid"

sim-config-prod: ## prod 시뮬 스택 병합 config 검증
	docker compose $(PROD_SIM_STACK) --env-file .env.prod config -q
	@echo "OK: prod sim merged compose config is valid"

sim-up: ## 로컬 시뮬 스택 기동(backend+관측+LB+k6-runner+reseed) — 곡선 시작
	docker compose $(LOCAL_SIM_STACK) up -d

sim-pause: ## k6-runner만 graceful stop(ramp-to-0) — reseed·LB·backend·관측은 유지
	docker compose $(LOCAL_SIM_STACK) stop k6-runner

sim-down: ## k6-runner·reseed graceful stop(ramp-to-0) — 나머지 스택은 무영향
	docker compose $(LOCAL_SIM_STACK) stop k6-runner reseed

sim-ps: ## 시뮬 컨테이너 상태 조회
	docker compose $(LOCAL_SIM_STACK) ps k6-runner reseed

sim-logs: ## k6-runner·reseed 로그 팔로우(진행률·gap report stdout·reseed 사이클 이력)
	docker compose $(LOCAL_SIM_STACK) logs -f k6-runner reseed

# ---- dev/prod 네임스페이스 변형 ----
sim-up-dev: ## dev 네임스페이스 시뮬 스택 기동(-p sports-dev --env-file .env.dev)
	docker compose -p sports-dev $(DEV_SIM_STACK) --env-file .env.dev up -d

sim-down-dev: ## dev 네임스페이스 k6-runner·reseed graceful stop
	docker compose -p sports-dev $(DEV_SIM_STACK) --env-file .env.dev stop k6-runner reseed

sim-up-prod: ## prod 네임스페이스 시뮬 스택 기동(-p sports-prod --env-file .env.prod) — 설계 전제(TDD "무엇을")
	docker compose -p sports-prod $(PROD_SIM_STACK) --env-file .env.prod up -d

sim-down-prod: ## prod 네임스페이스 k6-runner·reseed graceful stop
	docker compose -p sports-prod $(PROD_SIM_STACK) --env-file .env.prod stop k6-runner reseed
