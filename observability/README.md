# 옵저버빌리티 스택 (LGTM + OpenTelemetry Collector)

Spring Boot 백엔드의 메트릭·트레이스·로그를 수집·저장·시각화하는 로컬/dev/prod 관측 스택입니다.
근거: `../TDD.md`(Release Scenario·Testing Plan), `../ADR/ADR-002~005`, PRD FR-1·FR-2·FR-6·FR-7·FR-11.

- 메트릭: Prometheus 직접 scrape (앱 `/actuator/prometheus` + exporter 3종) — ADR-002
- 트레이스·로그: 앱 → OTel Collector(단일 게이트웨이) → Tempo(trace)/Loki(log) — ADR-003
- 시각화: Grafana (데이터소스·대시보드 자동 프로비저닝)

---

## 1. 파일 구성 (ADR-004 — compose 파일 분리)

| 파일 | 소유 | 내용 |
|---|---|---|
| `../docker-compose.yml` | 공용/⑧ | **base** — backend + 데이터스토어(mysql·mongodb·redis·kafka) + mock |
| `../docker-compose.dev.yml` / `.prod.yml` | ⑧ | 환경 override (호스트 포트, `APP_ENV`) |
| `../docker-compose.observability.yml` | ⑤ | 관측 백엔드 — OTel Collector·Prometheus·Tempo·Loki·Grafana |
| `../docker-compose.observability-agents.yml` | ⑤ | exporter 3종(mysqld·redis·kafka) + Kafka UI |
| `../Makefile` | ⑤(INFRA-05) | 위 파일들을 `-f` 병합 기동/정지하는 단일 진입점 |
| `../.env.observability.example` | ⑤(INFRA-05) | 관측 참조 변수 예시 |

> **⑧ base compose 기동이 선행 조건입니다.** 관측 에이전트(exporter)와 Collector는 base가 띄운
> 데이터스토어·backend에 **서비스명으로 접속**합니다. base 없이 관측 파일만 기동하면 exporter가
> 접속 대상(mysql·redis·kafka)을 찾지 못합니다. Makefile 타겟은 항상 base를 함께 병합합니다.

---

## 2. 기동 (Makefile)

레포 루트에서 실행합니다.

```bash
# 로컬(기본) — base + 관측 백엔드 + 관측 에이전트 병합 기동
make observability-up

# 정지(볼륨 유지) / 로그 팔로우 / 상태 조회 / config 검증
make observability-down
make observability-logs
make observability-ps
make observability-config
```

내부적으로 로컬 타겟은 다음과 동일합니다.

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.observability.yml \
  -f docker-compose.observability-agents.yml up -d
```

### dev / prod (환경 override)

`.env.dev` / `.env.prod` 는 배포 파이프라인(⑧)이 소유합니다. 해당 env 파일이 준비된 뒤 실행합니다.

```bash
make observability-dev    # -p sports-dev  --env-file .env.dev
make observability-prod   # -p sports-prod --env-file .env.prod
```

동일 명령 전개(dev):

```bash
docker compose -p sports-dev \
  -f docker-compose.yml \
  -f docker-compose.dev.yml \
  -f docker-compose.observability.yml \
  -f docker-compose.observability-agents.yml \
  --env-file .env.dev up -d
```

dev/prod는 `-p`(프로젝트명)로 네임스페이스가 분리되어 상호 무영향입니다. `APP_ENV` 값(`dev`/`prod`)이
메트릭 `env` 라벨로 유입되어(ADR-005) Grafana에서 환경별로 구분됩니다.

> ⑧의 `scripts/deploy.sh`/`rollback.sh`는 관측 레이어를 포함하지 않는 앱 배포 전용입니다.
> 관측 스택은 이 Makefile로 별도 레이어링합니다.

---

## 3. 환경 변수 (`.env.observability.example`)

`docker compose`는 로컬 실행 시 루트 `.env`를 자동 로드합니다. `.env.observability.example`의 키를
`.env`(로컬) 또는 `.env.dev`/`.env.prod`(⑧ 소유)로 복사해 값을 채웁니다. `.env*`는 커밋하지 않습니다.

| 변수 | 용도 | 예시 |
|---|---|---|
| `APP_ENV` | 메트릭 `env` 라벨(ADR-005) | `local` / `dev` / `prod` |
| `MANAGEMENT_OTLP_TRACING_ENDPOINT` | 앱 trace 발신 대상(HTTP) | `http://otel-collector:4318/v1/traces` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTEL SDK base 엔드포인트 | `http://otel-collector:4318` |
| `OTEL_SERVICE_NAME` | 서비스 식별(service.name) | BE=`sports-application` / web=`sports-web` |
| `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` | Grafana 관리자 계정 | `admin` / `admin` |

> **service.name 규약**: BE(Spring Boot)=`sports-application`, web(프론트)=`sports-web`.
> 서비스마다 다른 값을 부여합니다(같은 값을 공유하면 Tempo에서 trace가 한 서비스로 뭉칩니다).
> backend의 OTLP 엔드포인트는 `docker-compose.observability.yml`이 이미 서비스명 기준으로 주입하므로,
> `.env`의 위 값은 비컨테이너 로컬 앱(`gradlew bootRun`)이나 web처럼 컨테이너 밖 발신 시 참조용입니다.

---

## 4. 접근 (FR-7 Operations)

| 대상 | URL | 계정/비고 |
|---|---|---|
| **Grafana** | http://localhost:3000 | `admin` / `admin` (기본값, `GRAFANA_ADMIN_*`로 변경) |
| Prometheus | http://localhost:9090 | scrape 상태 `Status → Targets`, `up` 쿼리 |
| Tempo (query API) | http://localhost:3200 | Grafana Tempo 데이터소스가 프록시 |
| Loki | http://localhost:3100 | Grafana Loki 데이터소스가 프록시 |
| Kafka UI | http://localhost:8089 | consumer lag·토픽 조회(FR-6) |
| OTel Collector self-metric | http://localhost:8888 | Prometheus `job="otel-collector"`가 scrape |
| exporter (mysqld/redis/kafka) | :9104 / :9121 / :9308 | `/metrics` |

### 데이터소스 (자동 프로비저닝)

`grafana/provisioning/datasources/datasources.yml`이 Prometheus(default)·Tempo·Loki를 등록하고,
상호 연결(FR-11)을 배선합니다.

- metric → trace: Prometheus exemplar의 `trace_id` → Tempo 점프
- trace → log: Tempo `tracesToLogsV2` → Loki (`service.name` 태그 매칭)
- log → trace: Loki `derivedFields`가 로그의 `trace_id`(JSON·logfmt) → Tempo 점프

### 대시보드 4종 (자동 로드)

`grafana/provisioning/dashboards/provider.yml`이 `grafana/dashboards/*.json`을
**"Sports Observability"** 폴더로 로드합니다. Grafana 좌측 `Dashboards → Sports Observability`.

| 파일 | 대시보드 |
|---|---|
| `spring.json` | Spring Boot (JVM) — 메모리·스레드·executor·HTTP P95 |
| `mysql.json` | MySQL — connections·queries·버퍼풀 |
| `redis.json` | Redis — 명령·메모리·키 |
| `kafka.json` | Kafka — consumer lag·토픽·파티션 |

env 라벨로 환경을 구분해 조회합니다(`env=local|dev|prod`).

---

## 5. ⑦/⑧ override 확장법

새 override 파일은 자기 파일만 소유하고 병합 지점에 `-f`로 끼웁니다(ADR-004 Single Writer).

- **⑧ 환경 override** (`docker-compose.dev.yml`/`.prod.yml`): 호스트 포트·`APP_ENV`만 부여.
  base 서비스명·내부 포트는 재정의하지 않습니다(오타 시 신규 서비스가 생깁니다).
- **⑦ 스케일** (`docker-compose.scale.yml` 예정): backend 인스턴스 N개 + nginx LB.
  Prometheus `app` 잡의 targets 리스트에 `backend-2:8080 …`을 추가하면 다중 인스턴스가 scrape됩니다
  (`prometheus/prometheus.yml`에 확장 예시 주석 존재).
- 병합 순서: `base → 환경 override → 관측 백엔드 → 관측 에이전트`. 뒤 파일이 앞 파일에 deep-merge됩니다.

---

## 6. E2E 검증 절차 (Testing Plan scenario / Success Metrics)

> **주의 — 실기동 대체**: 아래 절차는 LGTM 실기동 환경에서 수행합니다. 관측 이미지(Grafana·Tempo·Loki·
> Prometheus·Collector·exporter)는 최초 pull 용량이 커, 본 티켓 검증은 **병합 compose config 유효성
> 검증(`docker compose ... config -q` exit 0)으로 대체**했습니다(§7). trace 연결율 99% 실측·대시보드
> 렌더·lag 조회는 실기동 환경에서 아래 절차로 수행해야 확정됩니다.

### 6-1. 기동·헬스 (NFR — 메모리 8GB 이내)

```bash
make observability-up
make observability-ps     # 전 컨테이너 State=running / health=healthy 확인
docker stats --no-stream  # 합계 메모리 8GB 이내 확인
```

### 6-2. trace 연결율 99% (Success Metric — 임의 API 10건 → Tempo 9건+ 단일 trace)

1. backend에 임의 API를 10건 호출합니다(예: `for i in $(seq 10); do curl -s localhost:8080/actuator/health >/dev/null; done` — 실제 비즈니스 엔드포인트 권장).
2. 서버 로그 MDC에 `trace_id`/`span_id`가 찍히는지 확인합니다.
3. Grafana → Explore → **Tempo** → Search에서 `service.name=sports-application` 최근 trace를 조회합니다.
4. 10건 중 **9건 이상**이 단일 trace(HTTP→DB/Gateway span 트리)로 조회되면 연결율 99% 충족입니다.
5. Kafka 경계 trace: Producer→Consumer가 **하나의 trace**로 이어지는지 확인합니다
   (`isObservationEnabled=true` 전파 검증 — false면 trace가 분리됩니다).

### 6-3. 대시보드 렌더 (P95 3초 이내 로딩)

Grafana → Dashboards → **Sports Observability** → 4종 대시보드가 실데이터로 패널을 렌더하는지,
로딩이 3초 이내인지 확인합니다. `env` 변수로 환경 전환이 동작하는지 확인합니다.

### 6-4. Kafka consumer lag (FR-6)

Kafka UI(http://localhost:8089) → Consumers → consumer group별 lag이 실시간 조회되는지 확인합니다.

### 6-5. 장애 격리 (User Scenario 4 — Collector 정지에도 메트릭 지속)

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml \
  -f docker-compose.observability-agents.yml stop otel-collector
curl -s localhost:8080/actuator/prometheus | head   # 여전히 200·메트릭 노출 확인
```

Prometheus에서 `up{job="otel-collector"} == 0`으로 Collector 다운이 감지되는지 확인합니다
(앱 `app` 잡의 `up`은 계속 1 — trace만 끊기고 메트릭 scrape는 지속).

### 6-6. 롤백 (스택별 독립)

```bash
docker compose -f docker-compose.observability.yml down   # 관측 백엔드만 정지, 앱·데이터스토어 유지 (레포 루트에서 실행)
```

trace만 끄려면 앱의 `OTEL_EXPORTER_OTLP_ENDPOINT`를 언셋(no-op)합니다(Release Scenario).

---

## 7. 본 티켓 검증 결과 (INFRA-05)

병합 compose config 유효성을 검증했습니다(구조 오류 시 non-zero exit).

| 검증 | 명령 | 결과 |
|---|---|---|
| 로컬 병합 | `docker compose -f docker-compose.yml -f docker-compose.observability.yml -f docker-compose.observability-agents.yml config -q` | exit 0 |
| dev 병합 | `… -f docker-compose.dev.yml … config -q` | exit 0 |
| prod 병합 | `… -f docker-compose.prod.yml … config -q` | exit 0 (`DOCKER_TAG` 미설정 경고 — `.env.prod`는 ⑧ 소유) |
| Makefile 파싱 | `make -n observability-up` 등 | 정상 |

LGTM 실기동 E2E(연결율 99% 실측·대시보드 렌더·lag)는 이미지 pull 용량으로 config 검증으로 대체했으며,
실기동 시 §6 절차로 수행합니다.
