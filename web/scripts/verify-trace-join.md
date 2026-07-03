# web↔BE trace 합류 검증 절차 (FE-03)

근거: `design-fe-web.md` "Testing Plan" scenario(E2E/수동) · PRD Success Metrics(연결율 99%, 샘플 10건 중 9건 이상).

이 문서는 `scripts/verify-trace-join.mjs`를 실 스택(Collector·Tempo·BE·web) 위에서 실행하는 절차와,
스크립트 대신 수동으로 확인하는 절차를 함께 기록한다. **본 세션에서는 Collector/Tempo/BE가 기동되지
않은 격리 worktree(`web/`만 작업)라 실기동 검증을 수행하지 못했다** — 아래 절차는 실 스택이 준비된
환경(로컬 docker compose 또는 dev 서버)에서 수행한다.

## 사전 조건

1. `docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d` 로 Collector·Tempo·Prometheus·Grafana·BE 기동.
2. web 서버 기동 시 다음 환경변수가 설정돼 있어야 한다.
   - `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` (docker 네트워크 내부라면 `http://otel-collector:4318`)
   - `OTEL_SERVICE_NAME=sports-web`
   - `APP_ENV=local` (또는 dev/prod)
   - `BACKEND_URL=http://localhost:8080` (BE 주소)
3. `npm run build && npm run start` 로 web을 Node 런타임에서 기동한다(`next dev`도 가능하나 프로덕션과
   동일한 조건을 위해 `build`+`start` 권장).

## 자동 스크립트 실행

```bash
cd web
WEB_BASE_URL=http://localhost:3000 \
TEMPO_BASE_URL=http://localhost:3200 \
REQUEST_COUNT=10 \
node scripts/verify-trace-join.mjs
```

- 종료 코드 `0`: 연결율(NFR 99%, 샘플 기준 9/10 이상) 충족.
- 종료 코드 `1`: 연결율 미달 — traceparent 전파 회귀 가능성. `lib/server/be-client.ts`·`instrumentation.ts` 재확인.
- 종료 코드 `2`: Tempo 접근 실패(실 스택 미기동) — 아래 수동 절차로 대체.

## 수동 절차 (스크립트 미기동 환경)

1. web에 임의 요청 10건을 보낸다(예: `for i in {1..10}; do curl -s http://localhost:3000/api/health > /dev/null; done`).
   `/api/health`는 `beClient`를 경유해 BE `/actuator/health`를 호출하므로 BFF 합류 검증에 적합하다.
2. Grafana(`http://localhost:3001` 또는 compose 설정 포트) → Explore → Tempo 데이터소스에서
   `{ service.name = "sports-web" }` 로 최근 trace 목록을 조회한다.
3. 임의 trace를 열어 span 트리에 `service.name = sports-application` span이 자식으로 포함되는지 확인한다.
4. 10개 trace 중 9개 이상에서 위 조건이 성립하면 연결율 기준(99%, 샘플 9/10) 충족으로 판단한다.
5. 조인된 trace의 web span·BE span이 동일한 `deployment.environment` 리소스 속성 값을 갖는지
   span 상세(Attributes) 탭에서 비교한다.

## 장애 격리 확인 (Collector 정지)

1. `docker compose -f docker-compose.observability.yml stop otel-collector`
2. web에 요청을 보내 정상 응답(200)이 오는지 확인한다 — export 실패가 요청 처리를 막지 않아야 한다
   (design-fe-web.md "실패 경로" 표 — Collector 다운 시 export best-effort, 요청 처리 무영향).
3. `docker compose -f docker-compose.observability.yml start otel-collector` 로 원복한다.

## 회귀 감지 (traceparent 전파 끄기)

`instrumentation.ts`를 임시로 비활성화(`next.config.mjs`의 `instrumentationHook: false` 또는
`instrumentation.ts` 파일명 변경)한 상태로 위 자동 스크립트를 재실행하면 연결율이 0%로 떨어져야
한다 — 이 상태 전이 자체가 traceparent 전파 실패를 감지하는 회귀 테스트 역할을 한다. 단위 테스트
레벨의 동일 원리 검증은 `lib/server/__tests__/be-client-tracing.test.ts`의
"OTel 계측 등록 전에는 traceparent가 없다(회귀 기준선)" 케이스가 담당한다.

## 결과 기록

실 스택에서 이 절차를 수행한 뒤 결과(연결율·env 태그 일관성 여부·수행 일시)를
`design-fe-web.md`(레포 밖 설계 문서 — FE 작업자 worktree 범위 밖) 또는 별도 QA 리포트에 기록한다.
본 세션은 격리 worktree(`web/`) 범위 제약으로 이 기록을 수행하지 못했다.
