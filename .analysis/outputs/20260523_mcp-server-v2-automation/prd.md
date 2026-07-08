# B2B MCP Server v2 — 운영자 워크플로우 자동화 (n8n / Zapier 통합) PRD

## 배경 (Background)

v1.0 B2B MCP Server MVP 가 dev 머지 완료된 상태입니다 (28 PR). 운영자는 Claude Desktop / ChatGPT / Cursor / Continue.dev / n8n 같은 클라이언트에서 **자연어로 수동 호출**하여 시설 / 굿즈 / 티켓 데이터를 조회·변경합니다.

v1.0 의 한계:

| 한계 | 운영 영향 |
|---|---|
| 정기 작업이 운영자 수동 의존 | 일일 매출 리포트 / 재고 점검 / 노쇼 환불 등을 매번 사람이 트리거 |
| 업무시간 외 자동 대응 불가 | 새벽 예약 충돌·재고 부족 발생 시 다음 업무일까지 방치 |
| 일관성 부족 | 같은 작업도 운영자마다 다른 시점·방식으로 처리 |
| confirm flow 가 자동화에 부적합 | write tool 의 2-step confirm 은 사람 입력 가정 — n8n 같은 도구는 첫 호출에서 다음 단계로 자동 진행 불가 |

v1.0 의 `McpToken.nonInteractive` 필드는 v1.0 V20 마이그레이션에 이미 존재하지만 실제 동작 분기는 없습니다 (placeholder). v2 는 이 필드를 활용하여 비대화형 자동화 시나리오를 본격 지원합니다.

### v2 추가 가치

1. **운영자 1인당 시간 절약** — 매일 30분 ~ 1시간 수동 클릭/명령 → 0분 (자동화 후)
2. **사고 대응 속도** — 비정상 패턴 (재고 부족 / 시설 예약 충돌) 발견에서 알림까지 5분 이내
3. **운영 일관성** — 같은 규칙으로 100% 동일 처리
4. **GA 진입 후 새로운 차별화 요소** — B2B 운영자 도구 시장의 자동화 수요 흡수

---

## 목표 (Goals)

- 비대화형 토큰의 confirm flow 자동 우회 — 명시적 scope `automation:bypass-confirm` 보유 시만 허용 (FR-01)
- Webhook tool 신설 — 운영자가 등록한 URL 로 이벤트 발생 시 outbound HTTP POST 발송 (FR-02)
- n8n / Zapier 공식 연동 — 어드민 UI 에서 1-click 워크플로우 import (FR-03)
- 자동화 토큰 별도 rate limit — 1000 req/min (vs 일반 토큰 600 req/min) (FR-04)
- Scheduled tool — cron 기반 정기 작업 (`scheduleAt`, `recurringSchedule`) (FR-05)
- Idempotency 키 강제 — 자동화 tool 호출에 `X-Idempotency-Key` 헤더 필수 + 24시간 중복 차단 (FR-06)
- 자동화 실패 Dead Letter Queue + 운영자 알림 (FR-07)

---

## 비목표 (Non-Goals)

- v1.0 의 28 PR 기능 재정의 — 기존 read/write tool 동작 변화 0
- v1.1 의 운영 안정화 항목 (실 PG / 도메인 / SOP / 부하 시험) — v1.1 PRD 별도
- 어드민 UI 의 인사이트/분석 대시보드 — 별도 PRD (인사이트 대시보드)
- n8n 자체 호스팅 — 운영자가 자체 또는 SaaS n8n 사용
- 운영자별 커스텀 워크플로우 빌더 — 템플릿 갤러리만 제공
- B2C 사용자 자동화 — B2B 운영자 전용

---

## 사용자 스토리 (User Stories)

| As a | I want to | So that |
|---|---|---|
| 운영자 | 매일 오전 9시에 매출 리포트가 Slack 으로 자동 발송되기를 | 출근 즉시 전일 매출 파악 |
| 운영자 | 재고 N개 미만 시 자동으로 알림 받기를 | 품절 전 발주 가능 |
| 운영자 | n8n 에서 1-click 으로 매출 리포트 워크플로우를 가져오기를 | n8n 사용법 모르고도 시작 가능 |
| 운영자 | 비대화형 토큰의 confirm 우회 권한을 별도 scope 로 통제하기를 | 자동 환불 같은 위험 작업은 명시적 허용 후만 가능 |
| 보안 담당자 | 자동화 토큰이 일반 토큰과 다른 rate limit 적용되기를 | 자동화 폭주 시 일반 사용에 영향 0 |
| 보안 담당자 | 모든 자동화 호출이 audit log 에 "automation=true" 마킹되기를 | 사후 추적 가능 |
| 운영자 | 같은 Idempotency 키 호출은 한 번만 실행되기를 | n8n 재시도로 중복 환불 사고 방지 |
| 운영팀 | 자동화 실패 시 DLQ + Slack 알림 받기를 | 운영자 미인지 사고 0 |
| 운영팀 | 자동화 토큰 발급 시 일반 토큰과 명시적으로 구분되기를 | 사용자 혼동 방지 |
| Legal 담당 | 자동 환불 정책이 명시적 의사결정 후 활성화되기를 | 무단 자동 환불 사고 방지 |

---

## 기능 요구사항 (Functional Requirements)

### FR-01. non_interactive 토큰의 confirm flow 자동 우회

**행위자**: 운영자가 `non_interactive=true` + scope `automation:bypass-confirm` 보유 토큰으로 write tool 호출.

**트리거**: write tool (`refundBooking`, `cancelBooking`, `createSlot` 등) 의 1차 호출.

**결과**:
- non_interactive=false 토큰 → 기존 v1.0 동작 (2-step confirm)
- non_interactive=true + scope 보유 토큰 → 1차 호출에서 직접 실행 (confirm 단계 생략)
- non_interactive=true + scope 미보유 토큰 → 403 Forbidden (scope 명시 요구 에러)
- 우회 실행 시 audit log 에 `bypass_confirm=true` 마킹
- 우회 실행 시에도 paramsHash 검증 대체로 **request body hash + Idempotency 키 검증** 적용 (FR-06 연계)

**v2.0.0 출시 범위 (CI-02 결정 반영)**:
v2.0.0 = FR-01 + FR-06 (Idempotency) + **FR-07 최소 구현 (DLQ 적재만, 알림은 v2.0.1)** 동반 출시. FR-01 우회 실행 실패 시 DLQ 경로가 v2.0.0 부터 보장됩니다. 알림 채널 (Slack DM / Email DL) 은 v2.0.1 에서 운영자에 노출.

**Legal 게이트 분할 출시 (Open Issue #1 부분 해소)**:
Legal 자동 환불 정책 결정이 지연될 경우, 환불 외 write tool (`createSlot`, `updateSlot`, `deleteSlot`, `issueComplimentaryTicket`) 만 confirm 우회 활성화하고 `refundBooking` 은 Legal 결정 후 별도 활성화 (scope `automation:bypass-confirm:refund` 분리 가능 — Open Issue #14).

### FR-02. Webhook tool — 이벤트 구독 및 outbound 호출

**행위자**: 운영자가 어드민 UI 에서 webhook 등록.

**트리거**: MCP server 내부 이벤트 발생 (예: `BookingCancelledEvent`, `McpAnomalyDetectedEvent`, `LowInventoryEvent`).

**결과**:
- 운영자가 등록한 webhook 정보 — URL, secret, 구독 이벤트 목록, 활성 여부 — `mcp_webhooks` 테이블 (V26 마이그레이션)
- 이벤트 발생 시 운영자 본인 webhook 으로 HTTP POST (`Content-Type: application/json` + `X-MCP-Signature: HMAC-SHA256(secret, body)`)
- 5초 timeout + 3회 retry (지수 백오프) — 실패 시 DLQ (FR-07)
- 운영자 본인 webhook 만 호출 — 타인 webhook URL 검증 (IDOR 차단)
- 운영자가 webhook URL 의 비-내부망 도메인 검증 (SSRF 방지 — localhost / 사내 IP 거부)

### FR-03. n8n / Zapier 공식 연동 — 워크플로우 템플릿 갤러리

**행위자**: 운영자가 어드민 UI `/admin/mcp/automation` 페이지 방문.

**트리거**: "n8n 으로 가져오기" 버튼 클릭.

**결과**:
- 어드민 UI 에 4 ~ 6 개 기본 워크플로우 템플릿 카드 노출 (일일 매출 리포트 / 재고 부족 알림 / 노쇼 환불 / 이벤트 시작 30분 전 푸시 등)
- 각 카드 = n8n 호환 JSON workflow export (HTTP node + Slack/Email node + Schedule node)
- 1-click 다운로드 → 운영자가 자기 n8n 인스턴스 에 import
- n8n workflow JSON 의 HTTP 노드에 MCP endpoint URL placeholder + `Authorization: Bearer <ENV>` 형태로 사전 작성 — 운영자는 토큰 환경변수만 등록
- Zapier 도 동일 구조 — Zapier "Webhooks by Zapier" + "Schedule by Zapier" 조합 (Zapier 공식 통합은 비목표)
- 템플릿 갤러리는 정적 파일 (`web/public/automation-templates/`) — DB 의존 0
- **각 템플릿 JSON 에 `schema_version` 필드 포함** — MCP API 호환 버전 명시. 어드민 UI 다운로드 시 운영자 토큰의 MCP API 버전 (`mcp.api.version` 응답 헤더) 와 비교 → 불일치 시 경고 모달 ("구버전 템플릿 — 호환 안 될 수 있음"). 템플릿 변경 시 `schema_version` increment + `CHANGELOG.md` 기록 강제.

### FR-04. 자동화 토큰 별도 rate limit

**행위자**: nginx (외부 진입점) + Spring Boot (토큰 타입 판별).

**트리거**: 토큰 인증 시점에서 `non_interactive` 필드 확인.

**구현 메커니즘 (필수 명세)**:

토큰 타입 판별 방법은 다음 옵션 중 1개를 선택합니다 (Open Issue #13):

| 옵션 | 동작 | 장점 | 단점 |
|---|---|---|---|
| **A. nginx auth_request 서브루틴** | nginx 가 `/internal/mcp/token-type` 으로 Spring 호출 → Spring 이 토큰 분류 응답 헤더 (`X-MCP-Token-Type: interactive\|non_interactive`) → nginx `map` 블록이 헤더 기반 zone 결정 | 표준 패턴, nginx 가 rate limit 본연 위치 | 요청당 추가 내부 호출 1회 (Spring 부하) |
| **B. Spring Bucket4j (in-application rate limit)** | nginx 는 IP rate 만 처리 (미인증 100/min) + Spring 의 `McpTokenAuthenticationFilter` 가 Bucket4j 로 토큰 타입별 rate 분기 | 추가 내부 호출 0, BE 단일 책임 | nginx 본연 책임 위반, BE 메모리 사용 |
| **C. JWT claim 포함** | 토큰 발급 시 `non_interactive` 를 JWT claim 에 포함 → nginx 가 JWT decode → claim 기반 zone | nginx 단독 처리 | v1.0 의 bearer 토큰 (bcrypt 해시) 구조와 불일치, JWT 재설계 필요 |

**결과**:
- non_interactive=true 토큰: 1000 req/min (key: token_id)
- non_interactive=false 토큰: 600 req/min (v1.0 기존)
- 미인증: 100 req/min (IP — v1.0 기존)
- 자동화 토큰 80% 도달 시 운영자 통지 (`McpAnomalyDetectedEvent` 와 별도 채널 — Open Issue #4)
- nginx config `infra/nginx/mcp.conf` 의 `map` 블록 확장 — `$mcp_token_type` 변수 도입 (옵션 A/C 채택 시)
- Spring Boot `McpTokenAuthenticationFilter` 후속 Bucket4j 어댑터 추가 (옵션 B 채택 시)

### FR-05. Scheduled tool — cron 기반 정기 작업

**행위자**: 운영자가 어드민 UI 또는 MCP tool 호출로 스케줄 등록.

**트리거**: 등록된 cron 표현식 일치 시점.

**결과**:
- 신규 tool `scheduleAt(toolName, params, runAtIso)` — 1회 예약 실행
- 신규 tool `recurringSchedule(toolName, params, cronExpr)` — 정기 실행 등록
- `mcp_scheduled_jobs` 테이블 (V27 마이그레이션) — job_id, owner_user_id, **tokenId**, tool_name, params_json, cron_expr, next_run_at, enabled
  - **tokenId 저장 이유**: scheduled executor 가 job 실행 시 저장된 tokenId 로 토큰을 재로드 → 토큰의 scope 목록 기반 SecurityContext 주입 (REC-01 반영)
  - **`params_json` 저장 정책**: PII 포함 가능 (예: 환불 대상 booking 의 사용자 정보) → PiiMasker 적용 후 저장 vs AES-256 컬럼 암호화 → Open Issue #17 결정. 결정 전까지 PiiMasker 사전 적용 가정.
- Spring `@Scheduled` + ShedLock 또는 Quartz cluster mode (Open Issue #5 결정) — 1분마다 `next_run_at <= NOW()` job 조회 후 실행
- **Scheduled executor 의 SecurityContext 주입 메커니즘 (REC-01 반영)**:
  - 실행 시점에 `mcp_tokens` 에서 tokenId 로 토큰 + scope 재로드
  - `McpAuthenticatedPrincipal` 동일 인터페이스로 SecurityContext 에 set
  - `@PreAuthorize("@authz.hasMcpScope(...)")` 가드가 일반 호출과 동일하게 동작
  - 토큰이 폐기됐거나 만료됐으면 job 자동 비활성 (`enabled=false`) + 운영자 알림
- 실행 결과 audit log + 실패 시 DLQ (FR-07)
- 최대 동시 활성 schedule: 운영자당 50개 (운영자 토큰 무한 schedule 등록 방지)

### FR-06. Idempotency 키 강제 + 24시간 중복 차단

**행위자**: 자동화 도구 (n8n / Zapier / Scheduled tool) 가 MCP tool 호출 시.

**트리거**: 모든 write tool + `non_interactive=true` 토큰 read tool 호출.

**결과**:
- 자동화 호출에 `X-Idempotency-Key` 헤더 필수 (UUID v4 권장)
- 헤더 없는 자동화 호출 → 400 Bad Request `idempotency_key_required`
- 동일 키 24시간 내 재호출 → 첫 호출 결과 캐싱 응답 (DB / Redis 상관없이 동일 응답 — Open Issue #3)
- Idempotency 키 저장소: Redis `mcp:idempotency:{tokenId}:{key}` (TTL 24h) — Redis 다운 시 fail-open (호출 허용 + 운영자 알림) vs fail-close (호출 거부) — Open Issue #6
- 일반 (대화형) 토큰 read 호출은 Idempotency 미요구 (기존 v1.0 동작 유지)
- **non_interactive read tool 에도 Idempotency 강제하는 이유**: 자동화 도구 (n8n / Zapier) 의 retry 또는 다중 실행 시 운영자 모니터링/리포트 화면이 중복 데이터로 오염되는 것을 방지. 또한 audit log 의 중복 row 적재로 인한 Anomaly Detection false positive 발생을 차단.

### FR-07. Dead Letter Queue + 운영자 알림

**행위자**: MCP server 내부 (자동화 tool 실행 / webhook outbound / scheduled job).

**트리거**: 3회 retry 후 영구 실패.

**단계별 출시 (CI-02 결정 반영)**:

**(a) v2.0.0 최소 구현**:
- 실패 이벤트 DLQ 적재 — **Kafka 토픽 `mcp.dlq.v1`** (Open Issue #2 해소 — `KafkaDomainEventPublisher` 인프라 재사용, Redis Streams 신규 도입 불필요)
- DLQ 항목 구조: `failedAt`, `tokenId`, `toolName` 또는 `webhookId` 또는 `scheduleJobId`, `paramsMasked`, `error`, `retryCount`, `bypass_confirm` 플래그
- 운영자 알림 미포함 (적재만)

**(b) v2.0.1 전체 구현**:
- DLQ 적재 시 운영자 알림 (Slack DM 또는 Email — Open Issue #4)
- 어드민 UI `/admin/mcp/automation/dlq` 페이지 — 실패 목록 + 수동 재시도 / 폐기 버튼
- DLQ 항목 보관 30일 → 자동 만료

**`paramsMasked` 마스킹 기준 (REC-08 반영)**:
- BE-09 의 `PiiMasker` 동일 규칙 적용 (이름 / 전화 / 이메일 / 주소 / 생년월일 / 카드번호 / 계좌번호)
- 추가: webhook secret (`secret` 필드) 전체 마스킹, confirmationToken 전체 마스킹
- 환불 금액 (`amount`) 평문 허용 (감사 추적 필요)
- 보안팀 추가 검토 항목 — Open Issue #15 (DLQ 추가 마스킹 필드 정의)

---

## 비기능 요구사항 (Non-Functional Requirements)

| 분류 | 항목 | 기준 |
|---|---|---|
| 성능 | 자동화 write tool P95 | < 1500ms (v1.0 동등) |
| 성능 | webhook outbound 발송 P95 | < 500ms (timeout 5s 제외) |
| 성능 | scheduled job 트리거 지연 | < 1분 (cron 표기 시각 대비) |
| 신뢰성 | webhook retry 성공률 | > 95% (3회 retry 누적) |
| 신뢰성 | scheduled job 누락 (cron miss) | 0건 (1분 단위 polling 보장) |
| 신뢰성 | Idempotency 중복 실행 | 0건 (24h 윈도우 내) |
| 보안 | confirm 우회 시 명시 scope 검증 | 100% — scope 없으면 403 |
| 보안 | webhook URL SSRF 차단 | 사내 IP / localhost / metadata endpoint 0건 통과 |
| 보안 | webhook signature 검증 | HMAC-SHA256 강제 (운영자 secret) |
| 보안 | 자동화 audit log `bypass_confirm` 마킹 | 100% |
| 운영 | 자동화 80% rate 도달 운영자 통지 | 임계 초과 후 5분 이내 |
| 운영 | DLQ 적재 시 운영자 알림 | 적재 후 1분 이내 |
| 회귀 | v1.0 의 일반 토큰 동작 변화 | 0 (테스트로 검증) |

---

## 제약 조건 (Constraints)

- **자동 환불 정책 — Legal 결정 필요**: confirm 우회로 환불 자동 실행 시 사용자 동의/통지 의무 (소비자보호법) 검토 필요
- **n8n MCP 네이티브 지원 부재**: n8n 은 MCP 클라이언트 네이티브 지원 없음 — HTTP node + custom community node 활용 (v1.1 Open Issue #10 과 연동)
- **DLQ 인프라 결정 의존**: Kafka 토픽 또는 Redis Streams 결정 필요 — sports-application 의 Kafka 사용 현황과 정합 (Booking / Notification 등 다른 도메인 토픽 패턴 확인)
- **시크릿 관리**: webhook secret + PG 시크릿 (v1.1 Issue #9 와 동일 인프라) — 평문 커밋 0건
- **하위 호환성**: 기존 운영자 (non_interactive=false 토큰) 동작 변화 0 — v2 는 opt-in 만
- **외부 의존 (n8n / Zapier)**: 두 도구의 API / workflow JSON 스펙 변경에 영향 받음 — 분기별 호환성 점검 필요
- **운영자 secret 길이**: webhook secret 최소 32 char (bcrypt 또는 평문 저장 — Open Issue #7)
- **v1.1 INFRA-02 (nginx) 와 충돌 — 머지 순서 강제**: v2.0.3 진입 전 v1.1 INFRA-02 (nginx default config + WAF rate limit) 머지 완료 필수. v2.0.3 의 nginx config 변경 (`$mcp_token_type` 추가 또는 Bucket4j 분기) 은 v1.1 INFRA-02 base 위에서 작성. 동시 진입 시 머지 충돌 보장.
- **Scheduled job 다중 인스턴스 환경 대응**: 서버가 HPA / K8s replica 다중 배포 시 Spring `@Scheduled` 는 중복 실행 위험 — Quartz cluster mode 또는 ShedLock 필수 (Open Issue #5 에서 결정)
- **webhook 외부 호출 SSRF 차단 기준 (REC-02 반영)**: 차단 대상 IP 범위 — `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `127.0.0.0/8`, `169.254.0.0/16` (클라우드 메타데이터), `::1` (IPv6 루프백), `fd00::/8` (IPv6 ULA). 보안팀 추가 리뷰 필수 (Open Issue #16).

---

## 영향 범위 (Scope)

| 레포 / 디렉토리 | 변경 유형 | 설명 |
|---|---|---|
| `backend/src/main/kotlin/com/sportsapp/domain/mcp/McpToken.kt` | 수정 | non_interactive + bypass-confirm scope 검증 메서드 추가 |
| `backend/src/main/kotlin/com/sportsapp/domain/mcp/McpWebhook.kt` | 신규 | Webhook Entity + Repository interface |
| `backend/src/main/kotlin/com/sportsapp/domain/mcp/McpScheduledJob.kt` | 신규 | Scheduled job Entity + Repository interface |
| `backend/src/main/kotlin/com/sportsapp/application/mcp/*` | 신규 | RegisterWebhookUseCase / RegisterScheduleUseCase / RetryDeadLetterUseCase 등 |
| `backend/src/main/kotlin/com/sportsapp/infrastructure/automation/*` | 신규 | Idempotency Redis adapter / DLQ producer / Webhook outbound client |
| `backend/src/main/kotlin/com/sportsapp/presentation/mcp/automation/*` | 신규 | scheduleAt / recurringSchedule / 자동화 admin API |
| `backend/src/main/kotlin/com/sportsapp/presentation/mcp/webhook/*` | 신규 | WebhookSubscriptionApiController + WebhookEventPublisher |
| `backend/src/main/kotlin/com/sportsapp/presentation/mcp/audit/McpToolAuditHelper.kt` | 수정 | bypass_confirm + automation flag 마킹 |
| `backend/src/main/resources/db/migration/V26__create_mcp_webhooks.sql` | 신규 | webhook 테이블 |
| `backend/src/main/resources/db/migration/V27__create_mcp_scheduled_jobs.sql` | 신규 | scheduled job 테이블 |
| `backend/src/main/resources/db/migration/V28__insert_automation_scopes.sql` | 신규 | `automation:bypass-confirm` 등 scope permission row |
| `infra/nginx/mcp.conf` | 수정 | non_interactive 별도 rate zone |
| `web/app/admin/mcp/automation/page.tsx` | 신규 | 워크플로우 갤러리 + 1-click n8n |
| `web/app/admin/mcp/automation/webhooks/page.tsx` | 신규 | webhook 등록/조회/삭제 |
| `web/app/admin/mcp/automation/schedules/page.tsx` | 신규 | scheduled job 등록/조회/삭제 |
| `web/app/admin/mcp/automation/dlq/page.tsx` | 신규 | DLQ 항목 + 수동 재시도 |
| `web/public/automation-templates/*.json` | 신규 | n8n 호환 워크플로우 4 ~ 6 개 |

---

## 오픈 이슈 (Open Issues)

기한은 절대 날짜 또는 마일스톤 진입 절대 기한으로 표기합니다.

| # | 질문 | 담당 | 기한 |
|---|---|---|---|
| 1 | 자동 환불 정책 — 소비자보호법상 사용자 사전 동의 필요 여부 + 자동 통지 의무 [외부 의존 — Legal] | Legal + PM | **2026-06-15 (외부 게이트)** |
| 2 | ~~DLQ 인프라~~ | ~~BE + DevOps~~ | **해소 (2026-05-23) — Kafka 토픽 `mcp.dlq.v1` 채택. `KafkaDomainEventPublisher` + Booking 도메인 이벤트 인프라 재사용** |
| 3 | ~~Idempotency 응답 캐싱~~ | ~~BE + 보안~~ | **해소 (2026-05-23) — `previous=true` 플래그 추가. n8n / Zapier 워크플로우 재시도 추적용. MCP 스펙 확장 필드** |
| 4 | 자동화 80% rate / DLQ 적재 운영자 알림 채널 — Slack DM / Email DL / in-app push (v1.0 BE-08b 와 채널 통합 가능성) | 운영팀 + PM | 2026-07-14 (v2.0.1 진입 14일 전) |
| 5 | Scheduled job 실행 엔진 — Spring `@Scheduled` + ShedLock (단순) vs Quartz cluster mode (HA) — 현재 K8s replica 수 확인 후 결정 [외부 의존 — DevOps K8s 확인 필요] | BE + DevOps | **2026-07-28** |
| 6 | ~~Redis 다운 시 정책~~ | ~~보안 + 운영팀~~ | **해소 (2026-05-23) — fail-close (호출 거부) + Redis 다운 어드민 알림. 환불/슬롯 중복 방지 우선** |
| 7 | ~~webhook secret 저장 방식~~ | ~~보안 + PM~~ | **해소 (2026-05-23) — bcrypt 해시. 분실 시 재발급. DB 유출 시에도 webhook 검증 secret 노출 없음** |
| 8 | ~~webhook 최대 개수~~ | ~~PM~~ | **해소 (2026-05-23) — 운영자당 10개. 일반 운영자 시나리오 (재고/노쇼/환불/알림/이벤트/리포트) 충분 + SSRF/스팸 익스포저 제한** |
| 9 | ~~워크플로우 템플릿 우선순위~~ | ~~PM + 운영팀~~ | **해소 (2026-05-23) — 4개: 일일 매출 리포트 / 재고 부족 Slack 알림 / 노쇼 자동 환불 안내 / 이벤트 시작 전 자동 푸시** |
| 10 | ~~자동화 토큰 발급 한도~~ | ~~보안 + 운영팀~~ | **해소 (2026-05-23) — 운영자당 최대 3개 (운영 1 / 교체 대기 1 / 예비 1) + 토큰 회전 무중단 정책** |
| 11 | n8n MCP 연동 검증 — community node 가용성 + 검증 PoC (v1.1 Open Issue #10 과 통합) [외부 의존 — TPM 게이트 #K] | TPM | **v1.1 #K 결과 통보 시 (2026-05-30 게이트)** |
| 12 | ~~Zapier 공식 통합~~ | ~~PM~~ | **해소 (2026-05-23) — v2 GA 후 검토 (v2 범위 비목표 확정). v2 에서는 HTTP node 만 지원** |
| 13 | nginx 토큰 타입 판별 메커니즘 (옵션 A nginx auth_request / B Spring Bucket4j / C JWT claim) | BE + DevOps | 2026-07-28 (v2.0.2 진입 14일 전, v2.0.3 의 nginx config 작성 전) |
| 14 | ~~confirm 우회 scope 분할~~ | ~~보안 + PM + Legal~~ | **해소 (2026-05-23) — `:refund` 별도 scope 분리. Legal #1 지연 시 환불 외 4개 tool 먼저 출시 가능** |
| 15 | ~~DLQ paramsMasked 추가 마스킹~~ | ~~보안~~ | **해소 (2026-05-23) — PiiMasker 기본 + secret + confirmationToken 만. 추가 필드 불필요** |
| 16 | webhook SSRF 차단 IP 범위 최종 확정 + 보안팀 리뷰 통과 [외부 의존 — 보안팀] | 보안 | **2026-07-14** |
| 17 | `mcp_scheduled_jobs.params_json` 저장 정책 — 평문 vs PiiMasker 적용 vs AES-256 컬럼 암호화 [외부 의존 — 보안] | 보안 + BE | **2026-07-28** |
| 18 | ~~FE analytics 인프라~~ | ~~FE + DevOps~~ | **해소 (2026-05-23) — Mixpanel/GA4/Datadog RUM 0건. v2.0.3 GA G4 (어드민 클릭 -30%) 는 비목표 분리 또는 BFF 서버 로그 우회 결정 필요** |

---

## 마일스톤

각 milestone 은 Open Issue 결정 후 진입합니다.

| 단계 | 내용 | 진입 조건 | 기한 |
|---|---|---|---|
| **v2.0.0** | FR-01 (non_interactive + bypass-confirm scope) + FR-06 (Idempotency 키) + FR-07(a) DLQ 최소 적재 + V28 scope 마이그레이션 | Issue #1 (Legal 자동 환불 정책) + #2 (DLQ 인프라 결정) + #3 (Idempotency 응답) + #6 (Redis 다운 정책) + #10 (토큰 발급 한도) + #14 (환불 우회 scope 분할) + #15 (DLQ paramsMasked 마스킹 기준) 해소 + DLQ 인프라 프로비저닝 완료 | 위 7건 해소 후 21일 이내 |
| **v2.0.1** | FR-02 (Webhook tool) + FR-07(b) DLQ 알림 + 어드민 UI + V26 webhook 마이그레이션 | Issue #4 (알림 채널) + #7 (webhook secret) + #8 (개수 제한) + #16 (SSRF IP 범위) 해소 + v2.0.0 머지 | 위 5건 해소 후 21일 이내 |
| **v2.0.2** | FR-05 (Scheduled tool) + V27 scheduled_jobs 마이그레이션 | Issue #5 (실행 엔진) + #17 (params_json 마스킹) 해소 + v2.0.1 머지 | 위 3건 충족 후 14일 이내 |
| **v2.0.3** | FR-03 (n8n 워크플로우 갤러리) + FR-04 (rate limit 분리) | Issue #9 (템플릿 우선순위) + #11 (n8n 검증) + #13 (nginx 메커니즘) + #18 (FE analytics) 해소 + v1.1 INFRA-02 적용 + v2.0.2 머지 | 위 6건 충족 후 14일 이내 |
| **v2.1.0** | Zapier / Make 추가 통합 + 부하 시험 합격 (v1.1 기준 동등 + 자동화 시나리오 추가) | Issue #12 (Zapier 통합 검토) + v2.0.3 머지 + staging 부하 환경 준비 | v2.0.3 머지 후 28일 이내 |
| **v2 GA** | 베타 5팀 자동화 사용 + G-자동화 메트릭 합격 + 부하 시험 합격 | v2.0.3 머지 + 1주 dry-run + v2.1.0 의 부하 시험 합격 결과 (Zapier 통합은 GA 비차단) | v2.0.3 합격 + 부하 합격 후 14일 |

### GA 진입 메트릭 (G-자동화)

| 지표 | 목표 | 측정 방법 |
|---|---|---|
| 자동화 운영자 (월 자동화 호출 ≥ 100건) | 베타 5팀 중 3팀 이상 | `mcp_audit_logs` 의 `bypass_confirm=true` 또는 scheduled job 호출 카운트 / 운영자별 집계 |
| 워크플로우 템플릿 1-click 사용 성공률 | > 90% | 다운로드 클릭 이벤트 (FE analytics) + 다운로드 후 7일 내 운영자의 첫 자동화 호출 (MCP 토큰별 `automation=true` audit log) 매칭 — 매칭률 |
| 자동화 tool P95 응답 시간 | < 1500ms | Datadog `mcp_tool_latency_seconds{automation=true}` P95 |
| webhook outbound 성공률 (3회 retry 포함) | > 95% | webhook 발송 시도 / 성공 카운트 (Datadog `mcp_webhook_attempts_total`, `mcp_webhook_success_total`) |
| Scheduled job 누락 (cron miss) | 0건 (1주 측정) | `mcp_scheduled_jobs.next_run_at` vs 실제 실행 시점 차이 > 5분 row 카운트 |
| Idempotency 중복 실행 | 0건 (1주 측정) | 동일 `X-Idempotency-Key` 의 audit log row 카운트 > 1 인 경우 |
| DLQ 평균 적재 → 운영자 인지 시간 | < 5분 | DLQ insert 시각 vs 운영자 알림 채널 (Slack/Email) 발송 시각 차이 |

---

## 다음 단계 안내

- 본 PRD 의 Open Issue **18건** 중 Legal (#1/#14) + 인프라 결정 (#2/#5/#6/#13/#17/#18) 이 핵심 차단 요소입니다.
- 차단 해소 후 `/feature` 파이프라인 진입 권장 (다중 도메인 + Kafka 토픽 신규 가능성 + FE 신규 페이지 4개).
- v1.1 의 INFRA-02 (nginx config) 와 충돌 — v2.0.3 진입 전 v1.1 INFRA-02 머지 필요.

---

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-23 | v2 초안 작성 (7 FR / 13 NFR / 12 Open Issue / 5+1 Milestone) | Claude (메인 세션 + /prd 스킬) |
