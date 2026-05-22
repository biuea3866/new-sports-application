# observability — 자체 호스팅 모니터링 스택

메트릭 · 로그 · 트레이스를 **Grafana 한 화면**에서 봅니다. 앱 docker-compose 와 분리되어 독립 기동합니다.

## 구성

| 서비스 | 역할 | 접속 | 영속 볼륨 |
|---|---|---|---|
| Grafana | 통합 UI (메트릭·로그·트레이스) | http://localhost:3001 | `grafana-data` |
| Prometheus | 메트릭 저장 · scrape | http://localhost:9090 | `prometheus-data` |
| Loki | 로그 저장 | http://localhost:3100 | `loki-data` |
| Zipkin | 트레이스 수집 · 자체 UI | http://localhost:9411 | — |
| zipkin-mysql | Zipkin 트레이스 영속 저장소 | (내부 전용) | `zipkin-mysql-data` |
| otel-collector | web/mobile 트레이스 수신 허브 (OTLP → Zipkin) | OTLP gRPC 4317 / HTTP 4318 | — |

## 트레이스 전송 경로

| 출처 | 전송 방식 | 경유 |
|---|---|---|
| backend (Spring Boot) | Brave | → Zipkin 직접 (`/api/v2/spans`) |
| web (Next.js 서버 사이드) | OpenTelemetry OTLP | → otel-collector(4318) → Zipkin |
| mobile (Expo RN, dev 한정) | OpenTelemetry OTLP | → otel-collector(4318) → Zipkin |

mobile/web → backend 호출은 W3C `traceparent` 헤더가 전파되어 **한 트레이스**로 연결됩니다.

> Grafana 는 익명 Admin 로그인입니다 (로컬 전용). web(Next.js)이 3000 을 쓰므로 3001 로 노출합니다.

## 기동 / 종료

```bash
# 기동
docker compose -f observability/docker-compose.yml up -d

# 상태 확인
docker compose -f observability/docker-compose.yml ps

# 종료 (데이터 유지)
docker compose -f observability/docker-compose.yml down

# 완전 초기화 (영속 볼륨까지 삭제)
docker compose -f observability/docker-compose.yml down -v
```

## 백엔드 연동

`backend` 는 호스트에서 `./gradlew bootRun` 으로 실행합니다. 이미 다음이 설정돼 있습니다.

- **메트릭** — `/actuator/prometheus` 노출. Prometheus 가 `host.docker.internal:8080` 으로 15초마다 scrape.
- **트레이스** — Brave → Zipkin (`http://localhost:9411/api/v2/spans`). HTTP·JPA·Kafka 스팬 자동 계측.
- **로그** — Loki4j logback appender 가 `http://localhost:3100` 으로 전송. 로그 라인에 `traceId`/`spanId` 포함.

환경변수로 엔드포인트·샘플링을 덮어쓸 수 있습니다.

| 변수 | 기본값 | 용도 |
|---|---|---|
| `ZIPKIN_ENDPOINT` | `http://localhost:9411/api/v2/spans` | 트레이스 전송 대상 |
| `LOKI_URL` | `http://localhost:3100/loki/api/v1/push` | 로그 전송 대상 |
| `TRACING_SAMPLE_RATE` | `1.0` | 트레이스 샘플링 비율 (운영은 `0.1` 등) |
| `TRACING_PROPAGATION` | `W3C` | 트레이스 컨텍스트 전파 포맷 (web/OTel 과 일치) |

## web 연동

`web` 는 호스트에서 `npm run dev` 로 실행합니다. `@vercel/otel` 이 서버 사이드(SSR·Route Handler·RSC)를 자동 계측합니다.

- `web/instrumentation.ts` 가 `registerOTel` 호출 — `fetch` 자동 계측
- BFF Route Handler 의 `BACKEND_URL` 호출에 `traceparent` 전파 → 백엔드 트레이스와 연결
- 전송 대상은 `web/.env.local` 의 `OTEL_EXPORTER_OTLP_ENDPOINT` (기본 `http://localhost:4318`)

> 브라우저(클라이언트 사이드) RUM 은 이번 범위에 없습니다 — 서버 사이드 트레이싱만 포함.
> web 의 메트릭·로그는 Next.js 가 표준 노출 수단이 없어 제외했습니다.

## mobile 연동 (dev 한정)

`mobile` (Expo RN) 의 axios 호출을 OpenTelemetry span 으로 수동 계측합니다.

- `mobile/lib/observability/tracing.ts` — OTLP exporter + 트레이서 설정
- `mobile/lib/observability/tracing-interceptor.ts` — axios 인터셉터로 span 생성 + `traceparent` 주입
- `mobile/api/be-client.ts` 가 인터셉터를 부착 — API 호출이 백엔드 트레이스와 연결
- 전송 대상은 `mobile/.env` 의 `EXPO_PUBLIC_OTLP_ENDPOINT` (기본 `http://localhost:4318`)

> **dev/emulator 한정입니다.** 자체 호스팅 스택은 `localhost` 라 실기기·prod 빌드에서는 도달하지 못합니다.
> Android 에뮬레이터는 `EXPO_PUBLIC_OTLP_ENDPOINT=http://10.0.2.2:4318` 로, 실기기는 호스트 LAN IP 로 설정하세요.
> RN 은 OTel 자동 계측이 없어 axios 인터셉터로 수동 계측하며, OTLP exporter 의 RN 런타임 동작은 실기기 검증이 필요합니다.

## Grafana 사용법

http://localhost:3001 접속 후:

- **메트릭** — Explore → Prometheus. JVM·HikariCP·HTTP 요청 메트릭. `http.server.requests` 는 히스토그램이라 p95/p99 계산 가능.
- **로그** — Explore → Loki. `{app="sports-application"}` 쿼리. 로그의 `TraceID` 링크 클릭 시 Zipkin 트레이스로 점프.
- **트레이스** — Explore → Zipkin. 트레이스 검색 → 스팬 워터폴. 스팬에서 같은 시간대 Loki 로그로 점프.

## 주의

- 트레이스 화면은 Grafana Explore 의 기본 워터폴입니다. Grafana Cloud 의 Traces Drilldown / Application Observability 는 Tempo 전용이라 셀프호스팅에선 동작하지 않습니다. 트레이스 전용 UI 가 필요하면 Zipkin 자체 UI(http://localhost:9411)에 검색·워터폴·Dependencies(서비스 그래프)가 있습니다.
- 스택이 떠 있지 않아도 백엔드는 정상 동작합니다 — 트레이스/로그 전송 실패는 무시됩니다.
- 컨테이너 이미지 버전은 `docker-compose.yml` 에 고정돼 있습니다. 업그레이드 시 함께 검토하세요.
