# B2B MCP Server v1.1 — 운영 안정화 PRD

## 배경 (Background)

v1.0 B2B MCP Server MVP 가 dev 에 머지 완료된 상태입니다. 28 PR + 4 Decision A 로 Phase 1 (Read tool 8개 / Write tool 4개) + Phase 2 (Refund / Complimentary Ticket — PG stub) + 운영 인프라 (nginx/WAF default + k6 부하 시험 스크립트 + 토큰 유출 SOP 초안) + audit log 커버리지 보강 (BE-17b) 까지 머지됐습니다. harness-auditor 전수 감사 PASS (실질 위반 0건) 입니다.

다만 v1.0 머지 시점에 외부 게이트 (PG sandbox / DevOps 도메인 / Legal 통지 정책 / TPM PoC) 가 미해소 상태였기에 다음 항목이 stub · placeholder · sentinel 값으로 남아 있습니다.

| 항목 | 현재 상태 | 차단 사유 |
|---|---|---|
| 실 PG 환불 어댑터 | `StubPaymentRefundGateway` (`@Profile("!prod")`) | Open Issue #7 (PG sandbox) |
| MCP 노출 도메인 | `mcp-api.sportsapp.com` placeholder | 게이트 #B (DevOps 2026-05-27) |
| 토큰 유출 SOP 연락처 9곳 | `[보안 담당자 이메일]` 등 placeholder | 게이트 #C/#D/#L (Legal 2026-06-03) |
| `Ticket.ticketOrderId` | sentinel `0L` (NOT NULL 컬럼) | V25 마이그레이션 필요 |
| TEST-01 k6 부하 시험 | 스크립트만 작성, 실 실행 없음 | INFRA-02 적용 + staging 환경 |
| BE-08b Anomaly 임계값 | 기본값 (spike ratio 2.0, 절대값 50) | 운영 데이터 1주 수집 필요 |
| 베타 5팀 운영 | 미진행 | 게이트 #K (TPM PoC 2026-05-30) |

v1.1 은 위 7개 후속 항목을 외부 게이트 해소 시점에 따라 단계별로 마무리하여 **운영 진입 가능 상태** 로 끌어올립니다.

### 코드베이스 조사 결과 (2026-05-23 추가)

Open Issue 해소를 위해 origin/dev 28 PR 머지 상태를 점검한 결과:

| 항목 | 조사 결과 | Open Issue 영향 |
|---|---|---|
| 마이그레이션 번호 (origin/dev) | V1 ~ V24 머지 완료. mcp 관련은 V20 (tokens) / V21 (audit_logs) / V22 (permissions 14건) / V23 (stats/profile +2건) / V24 (Phase 2 +2건) | v1.1 = V25 / v2 = V26~V28 / 대시보드 = V29~V30 순서 확정 |
| Kafka 인프라 | `KafkaDomainEventPublisher.kt` + `BookingRequestedEvent` / `BookingCancelledEvent` / `BookingConfirmedEvent` 도메인 이벤트 존재 | v2 DLQ 결정 = **Kafka 토픽 `mcp.dlq.v1`** (별도 Redis Streams 도입 불필요) |
| roles 시드 | V2 = `USER / ADMIN / FACILITY_OWNER`, V19 = `EVENT_HOST / GOODS_SELLER`. **OPERATIONS_MANAGER 미존재** | 대시보드 #1 = OPERATIONS_MANAGER role 신설 + V?? 시드 작업 필수 |
| mcp permission 시드 | V22~V24 에 18건 (mcp.facility/booking/goods/ticketing/notification/operator/pii + Phase 2 refund/complimentary). **role_permissions 매핑 없음** | `.any` scope 토큰 발급은 운영팀장 신규 role 매핑 필요 |
| FE analytics 인프라 | Mixpanel / GA4 / Datadog RUM 0건 검색 | v2 GA G4 (어드민 클릭 -30%) → 비목표 분리 또는 BFF 서버 로그 우회 결정 필요 |

---

## 목표 (Goals)

- 실 PG 통합 후 운영 환경에서 환불/Complimentary Write tool 동작 (FR-1)
- MCP 도메인 prod / staging URL 확정 + 환경별 분리 (FR-2)
- 토큰 유출 SOP v1.1 — Legal 검토 통과 + placeholder 9곳 실 값 기입 (FR-3)
- `Ticket.ticketOrderId` nullable 전환 + sentinel 0L 제거 (FR-4)
- k6 부하 시험 합격 — P95 < 800ms read / < 1500ms write / 에러율 < 0.5% read / < 1% write (FR-5)
- BE-08b Anomaly 임계값을 운영 데이터 기반으로 재조정 — false positive 비율 1% 이내 (FR-6)
- 베타 5팀 온보딩 — 1주 dry-run 후 G1~G5 메트릭 측정 (FR-7)

---

## 비목표 (Non-Goals)

- Phase 3 신규 tool 추가 (v2 범위)
- 자동화 / Webhook / n8n 통합 (v2 범위)
- 신규 도메인 통합 (B2C 사용자 측 MCP, mobile MCP 등)
- 어드민 UI 신기능 추가 (인사이트 대시보드 별도 PRD)
- v1.0 의 기존 28 PR 동작 변경 — 회귀 0

---

## 사용자 스토리 (User Stories)

| As a | I want to | So that |
|---|---|---|
| 운영자 | MCP 환불 tool 호출 시 실 PG 사 (Toss/PortOne) 로 환불이 처리되기를 | 고객에게 실 환불 금액이 입금된다 |
| 운영자 | MCP 도메인 URL 이 prod / staging 별로 분리되기를 | 테스트 호출이 운영 데이터에 영향 주지 않는다 |
| 보안 담당자 | 토큰 유출 발생 시 명시된 SOP 의 절차·연락처대로 즉시 조치되기를 | 5분 이내 폐기 + 30분 이내 영향도 평가가 가능하다 |
| Legal 담당자 | SOP 의 규제 신고 기한·기관이 명시되기를 | 사고 발생 시 신고 의무를 충족한다 |
| DevOps | MCP staging 환경에 k6 부하 시험을 실 실행할 수 있기를 | 운영 도입 전 P95 합격 여부를 검증한다 |
| SRE | Anomaly Detection 의 false positive 가 1% 이내로 떨어지기를 | 알림 피로도 없이 실 이상 패턴만 대응한다 |
| 베타 운영자 | MCP tool 5클라이언트 (Claude Desktop / ChatGPT / Cursor / Continue.dev / n8n) 에서 동등 동작하기를 | 자기 환경에서 시작 5분 내 첫 조회가 가능하다 |
| PM | 베타 1주 dry-run 후 G1~G5 지표를 검토하기를 | GA 진입 의사결정의 근거가 있다 |

---

## 기능 요구사항 (Functional Requirements)

### FR-01. 실 PG 환불 어댑터 통합

**행위자**: 운영자가 `refundBooking` tool 호출 → BE 가 실 PG 환불 API 호출.

**트리거**: `RefundBookingUseCase` 가 `PaymentRefundGateway.requestRefund(paymentId, amount, reason)` 호출 시 prod 환경에서 stub 이 아닌 실 어댑터 동작.

**선행 조건 (필수)**:
- Open Issue #1 (PG 사 선정) 결정 완료
- Open Issue #9 (PG 시크릿 관리 방법 — AWS Secrets Manager / HashiCorp Vault / Spring Cloud Config 중) 결정 완료
- 결정된 시크릿 인프라 프로비저닝 완료 (별도 INFRA 티켓)

**결과**:
- prod 환경에서 `TossPaymentRefundGatewayImpl` (또는 PortOne 등 — Open Issue #1 결정) 활성화
- `@Profile("prod")` 어댑터 + `@Profile("!prod")` stub 분리 유지
- application-prod.yml 에 PG 시크릿 (`PAYMENT_GATEWAY_API_KEY`, `PAYMENT_GATEWAY_SECRET`) 참조 — Open Issue #9 결정된 방법으로 주입 (평문 커밋 0건)
- 어댑터 실패 시 retry (3회 / 지수 백오프 1s/2s/4s) + 영구 실패 시 `RefundBookingException` → audit log statusCode 502
- 스테이징에서 PG sandbox 로 E2E 시나리오 통과 (정상 환불 / 부분 환불 / 환불 거부 / 타임아웃)

### FR-02. MCP 도메인 prod / staging 분리

**행위자**: DevOps 결정 후 환경별 도메인 적용.

**트리거**: 게이트 #B (2026-05-27) 결과 통보.

**결과**:
- `MCP_EXTERNAL_URL` 환경변수가 prod 와 staging 에서 다른 값 (예: `https://mcp-api.sportsapp.com` vs `https://mcp-api-staging.sportsapp.com`)
- nginx config (`infra/nginx/mcp.conf`) 의 `server_name` 이 환경별 분리
- application-prod.yml / application-staging.yml 의 `mcp.external-url` 환경변수 override
- 토큰 발급 시 어드민 UI 에 prod URL 노출 — staging 토큰은 staging 환경 전용 명시
- SSL 인증서 발급 (Let's Encrypt 또는 사내 CA)

### FR-03. 토큰 유출 SOP v1.1 (Legal 검토 후)

**행위자**: 보안 담당자 + Legal.

**트리거**: 게이트 #C/#D/#L (2026-06-03) 결과 통보.

**결과**:
- `docs/security/mcp-token-leak-sop.md` placeholder 9곳 → 실 연락처/기한/기관 기입
- `mcp-incident-report-template.md` 의 "법적 보존 기간" 행 추가 (Legal 결정값)
- `mcp-token-best-practices.md` 의 "알림 채널" placeholder → Slack 채널명 또는 Email DL
- 사용자 통지 기한 (예: 72시간) 명시 — Legal 결정
- 규제 기관 신고 적용 조건 + 기한 + 기관명 명시 — Legal 결정
- SOP v1.1 사내 공유 + 보안팀 1회 dry-run 훈련
- **dry-run 합격 기준**: 가상 토큰 유출 시나리오에서 (a) 5분 이내 폐기 완료 (b) 30분 이내 영향도 평가 보고서 작성 (c) 24시간 이내 인시던트 보고서 초안 — 3개 모두 통과

### FR-04. Ticket.ticketOrderId nullable 전환 (V25)

**행위자**: Phase 2 의 Complimentary Ticket 발급 기능.

**트리거**: 운영자가 `issueComplimentaryTicket` tool 호출.

**선행 조건 (필수)**:
- Open Issue #4 (기존 `ticket_order_id = 0` 행 처리 정책) 해소 — BE + DBA 결정 완료
- v1.1.0 milestone 진입 전 결정 (게이트 #B 통보일 2026-05-27 이전)

**결과 — 별도 티켓 2개로 분리**:

**(a) [DB] V25 마이그레이션 (선행 배포)**
- `backend/src/main/resources/db/migration/V25__alter_tickets_ticket_order_id_nullable.sql` — `ALTER TABLE tickets MODIFY COLUMN ticket_order_id BIGINT NULL`
- 기존 `ticket_order_id = 0` 행 처리 (Open Issue #4 결정값 적용)
- 마이그레이션 적용 시점 = 애플리케이션 코드 배포 시점보다 반드시 선행 (단일 릴리즈인 경우 Flyway 가 부팅 시 적용 — 부팅 실패 시 자동 롤백)
- 롤백 SQL: `ALTER TABLE tickets MODIFY COLUMN ticket_order_id BIGINT NOT NULL DEFAULT 0` (단 NULL row 가 있으면 롤백 불가 — 별도 backfill 필요)

**(b) [BE] Ticket 모델 nullable 전환 (V25 적용 후 배포)**
- `Ticket.kt` 의 `ticketOrderId: Long` → `Long?` 변경
- `Ticket.issueComplimentary()` 팩토리: `ticketOrderId = null` (sentinel 0L 제거)
- `Ticket.ticketOrderId` 사용처 (JOIN 쿼리 등) 모두 nullable 안전 처리
- 단위 테스트: complimentary ticket 의 `ticketOrderId == null` 검증
- **배포 순서**: V25 적용 → 모니터링 1시간 → (b) 애플리케이션 배포

### FR-05. k6 부하 시험 실 실행

**행위자**: DevOps + QA.

**트리거**: INFRA-02 (nginx/WAF) 적용 + staging 환경 준비 완료.

**결과**:
- staging 환경에서 `test/load/mcp-read-load.js` 실 실행 — 200 VU + 50 RPS / 10분
- staging 환경에서 `test/load/mcp-write-load.js` 실 실행 — 100 VU + 20 RPS / 10분
- 합격 기준 (조건부) — Open Issue #11 결정에 따라 적용:
  - staging 스펙이 운영 동등 → 절대값 (read P95 < 800ms 에러율 < 0.5% / write P95 < 1500ms 에러율 < 1%)
  - staging 스펙이 운영 N% 축소 → 상대값 (운영 추정치 = staging × 동등성 비율) 적용
- 실패 시: 병목 원인 분석 (DB / Redis / nginx / Tomcat) + 튜닝 후 재시험
- 결과 리포트 `test/load/results/v1.1-load-test-{date}.md` 보관
- GA 진입 전 마지막 회귀 시험 — v1.1.1 (Phase 2 실 PG 통합) 머지 후 1회 더 실 실행 (v1.0 의 BE-03+16 결과물인 RefundBooking/Complimentary Ticket flow 가 실 PG 환경에서 부하 통과 확인)

### FR-06. Anomaly Detection 임계값 운영 데이터 기반 재조정

**행위자**: SRE + 보안 담당자.

**트리거**: 운영 데이터 1주 수집 (운영자 30명 / tool 호출 1만건 이상).

**결과**:
- `mcp_audit_logs` + `McpAnomalyDetectedEvent` 1주 분석
- 현재 임계값 (`BASELINE_WINDOW_DAYS=7`, `COLD_START_DAYS=14`, `SPIKE_RATIO=2.0`, `MIN_ABSOLUTE_THRESHOLD=50`) 의 false positive 비율 측정
- false negative 측정 여부 — Open Issue #13 결정 (미측정 결정 시 보안 담당자 명시 수용 기록 필수)
- false positive > 5% 시 임계값 조정 (spike ratio 2.5 또는 절대값 100 등 — Open Issue #6)
- `McpAnomalyDetector.companion object` 상수 갱신 + 운영 데이터 근거 코멘트 추가
- 조정 후 1주 재측정 — false positive < 1% 합격 + (#13 측정 결정 시) false negative 기록

### FR-07. 베타 5팀 진입

**행위자**: 베타 운영자 5팀 + PM + 운영팀.

**트리거**: 게이트 #K (2026-05-30) TPM PoC 보고 통과.

**결과**:
- 베타 운영자 5팀 선정 + 온보딩 문서 배포 (FE-03 의 docs 페이지 활용)
- 5클라이언트 (Claude Desktop / ChatGPT Desktop / Cursor / Continue.dev / n8n) 별 1팀씩 매칭 — n8n 은 Open Issue #10 결정에 따라 가능 여부 확인 (미지원 시 대체 클라이언트 Zed/Windsurf)
- G2 baseline 측정: 베타 1주 전부터 동일 운영자의 어드민 클릭 수 사전 집계 (Open Issue #12 결정 인프라 활용)
- 토큰 발급 + scope 설정 + 1주 dry-run
- 일일 운영 보고: tool 호출 수 / 에러 / Anomaly 발생 / 운영자 피드백
- 1주 후 G1~G5 메트릭 측정:
  - G1: 활성 운영자 5팀 (월 tool 10회 이상 호출)
  - G2: 어드민 클릭 50% 감소 (베타 운영자 1주 전/후 비교)
  - G3: confirm 미수신 write 실행 0건
  - G4: 신규 운영자 첫 조회까지 5분 이내
  - G5: throttling 80% 도달 동일 토큰 2회 이상 반복 0건
- G1~G5 합격 → GA 진입 의사결정 보고서

---

## 비기능 요구사항 (Non-Functional Requirements)

| 분류 | 항목 | 기준 |
|---|---|---|
| 성능 | read tool P95 | < 800ms |
| 성능 | write tool P95 (1차+2차 합산) | < 1500ms |
| 성능 | 동시 SSE 연결 | 200 (read) / 100 (write) |
| 신뢰성 | read tool 에러율 | < 0.5% |
| 신뢰성 | write tool 에러율 | < 1% |
| 신뢰성 | PG 어댑터 retry | 3회 / 지수 백오프 (1s/2s/4s) |
| 보안 | PG 시크릿 평문 커밋 | 0건 (환경변수 / vault) |
| 보안 | SOP placeholder 잔재 | 0건 (v1.1 종결 시) |
| 보안 | Anomaly false positive | < 1% (1주 재측정 후) |
| 회귀 | v1.0 28 PR 동작 변화 | 0건 (harness-auditor PASS 유지) |
| 회귀 | 보안 회귀 (IDOR / paramsHash / audit 누락) | 0건 |
| 관측성 | PG 호출 메트릭 (Datadog) | success/failure/latency 모두 노출 |
| 배포 | prod 어댑터 활성화 전 staging E2E | 4 시나리오 (정상/부분/거부/타임아웃) 통과 |

---

## 제약 조건 (Constraints)

- **PG sandbox 확보 의존** (Open Issue #7) — sandbox 미확보 시 FR-01 진행 불가. v1.1.0 milestone 보류.
- **Legal 검토 일정** (게이트 #C/#D/#L 2026-06-03) — FR-03 종결은 Legal 통과 후. 보안 사고 발생 시 v1.0 의 placeholder SOP 로 임시 대응.
- **DevOps 도메인 결정** (게이트 #B 2026-05-27) — FR-02 종결 트리거.
- **TPM PoC 보고** (게이트 #K 2026-05-30) — FR-07 진입 트리거.
- **운영 데이터 1주 수집** (FR-06) — 실 운영자 사용 시작 후 1주 (베타 진입 후).
- **하위 호환성** — `Ticket.ticketOrderId` 변경 시 기존 0L 데이터 마이그레이션 정책 결정 필요.
- **시크릿 관리** — PG 시크릿은 평문 커밋 0건 (환경변수 또는 vault 만).

---

## 영향 범위 (Scope)

| 레포 / 디렉토리 | 변경 유형 | 설명 |
|---|---|---|
| `backend/src/main/kotlin/com/sportsapp/infrastructure/payment/` | 신규 | `TossPaymentRefundGatewayImpl` (또는 PortOne) + `@Profile("prod")` |
| `backend/src/main/kotlin/com/sportsapp/domain/ticketing/Ticket.kt` | 수정 | `ticketOrderId: Long` → `Long?` |
| `backend/src/main/resources/db/migration/V25__*.sql` | 신규 | `tickets.ticket_order_id` nullable 전환 |
| `backend/src/main/resources/application-prod.yml` | 수정 | PG 시크릿 환경변수 + `MCP_EXTERNAL_URL` prod 값 |
| `backend/src/main/resources/application-staging.yml` | 신규 / 수정 | staging 도메인 + PG sandbox URL |
| `infra/nginx/mcp.conf` | 수정 | `server_name` 환경별 분리 |
| `infra/waf/mcp-rate-limit.tf` | 수정 | DevOps 결정 결과 반영 |
| `docs/security/mcp-token-leak-sop.md` | 수정 | placeholder 9곳 → 실 값 |
| `docs/security/mcp-incident-report-template.md` | 수정 | 법적 보존 기간 항목 추가 |
| `docs/security/mcp-token-best-practices.md` | 수정 | 알림 채널 placeholder 갱신 |
| `backend/src/main/kotlin/com/sportsapp/domain/mcp/McpAnomalyDetector.kt` | 수정 | 임계값 상수 운영 데이터 기반 갱신 |
| `test/load/results/` | 신규 | 실 실행 결과 리포트 |
| `web/` | 변경 없음 | FE 영향 0 — 운영 안정화는 BE/infra 중심 |

---

## 오픈 이슈 (Open Issues)

| # | 질문 | 담당 | 기한 |
|---|---|---|---|
| 1 | PG 사 선정 (Toss vs PortOne vs KakaoPay) — 수수료 / API 안정성 / sandbox 가용성 비교 [외부 의존 — Slack 발송 대기] | PM + DevOps | Issue #7 해소 시 |
| 2 | MCP 도메인 prod URL 확정 (`mcp-api.sportsapp.com` vs 서브 prefix 다른 형태) [외부 의존 — 게이트 #B] | DevOps | **2026-05-27 (게이트)** |
| 3 | 토큰 유출 시 사용자 통지 기한 (24h vs 72h) + 규제 기관 신고 적용 범위 [외부 의존 — 게이트 #C/#D/#L] | Legal | **2026-06-03 (게이트)** |
| 4 | ~~`ticket_order_id = 0` 처리~~ | ~~BE + DBA~~ | **해소 (2026-05-23) — NULL update 일괄. V25 에 `UPDATE tickets SET ticket_order_id = NULL WHERE ticket_order_id = 0` 포함** |
| 5 | k6 staging 환경의 DB / Redis 스펙 (운영과 동등 vs 축소) — 합격 기준 적용 가능 여부 [외부 의존 — #11 과 통합] | DevOps + QA | INFRA-02 적용 시 |
| 6 | ~~Anomaly 임계값 조정 기준~~ | ~~SRE + 보안~~ | **해소 (2026-05-23) — false positive 만 1% 이내 조정. false negative 미측정, 보안 담당자 명시 수용 + PRD 기록** |
| 7 | ~~베타 5팀 인센티브~~ | ~~PM~~ | **해소 (2026-05-23) — 베타 기간 (1개월) 플랫폼 수수료 면제. 게이트 #K 통과 후 5팀 매칭** |
| 8 | ~~PG 환불 실패 대응~~ | ~~운영팀 + PM~~ | **해소 (2026-05-23) — 자동 retry 3회 (지수 백오프) 후 모두 실패 시 어드민 inbox 알림 + 수동 개입** |
| 9 | PG 시크릿 관리 방법 (AWS Secrets Manager vs HashiCorp Vault vs Spring Cloud Config vs K8s Secret) — 인프라 준비 범위 확정용 [외부 의존 — DevOps + 보안] | DevOps + 보안 | FR-01 착수 14일 전 |
| 10 | ~~n8n 베타 포함 여부~~ | ~~TPM + 운영팀~~ | **해소 (2026-05-23) — 포함 시도 + 게이트 #K PoC 미지원 시 Zed / Windsurf 자동 fallback. v2 자동화 결합성 확보** |
| 11 | FR-05 staging 환경의 DB/Redis/Tomcat 스펙 — 운영 동등 vs 축소 (축소 시 합격 기준을 상대값으로 재정의 필요) [외부 의존 — #5 와 통합] | DevOps + QA | INFRA-02 적용 14일 전 |
| 12 | ~~G2 어드민 클릭 baseline~~ | ~~ops/analytics + PM~~ | **해소 (2026-05-23) — BFF 서버 access log (`/api/admin/*` GET 카운트) 기반. nginx/Spring access log 집계, 베타 1주 전부터 동일 기준 수집** |
| 13 | ~~Anomaly false negative 측정~~ | ~~보안 담당자 + SRE~~ | **해소 (2026-05-23) — #6 과 통합. 미측정 + 보안 담당자 수용 기록** |

---

## 마일스톤

게이트별로 분리하여 부분 해소 시점에 즉시 진행 가능하도록 구성합니다.

| 단계 | 내용 | 진입 조건 | 기한 |
|---|---|---|---|
| **v1.1.0-a** | FR-02 MCP 도메인 prod/staging 분리 | 게이트 #B (DevOps 2026-05-27) 해소 | #B 해소 후 7일 이내 |
| **v1.1.0-b** | FR-03 SOP Legal v1.1 placeholder 9곳 + dry-run | 게이트 #C/#D/#L (Legal 2026-06-03) 해소 | #C/#D/#L 해소 후 7일 이내 |
| **v1.1.0-c** | FR-04 V25 마이그레이션 + Ticket 모델 nullable 전환 | Open Issue #4 결정 완료 (v1.1.0-a 와 병행 가능) | Issue #4 해소 후 14일 이내 |
| **v1.1.1** | FR-01 실 PG 어댑터 통합 + staging E2E 4 시나리오 통과 | Open Issue #1 + #7 (PG sandbox) + #9 (시크릿 관리) 해소 + 시크릿 인프라 프로비저닝 완료 | 위 조건 충족 후 21일 이내 |
| **v1.1.2** | FR-05 k6 부하 시험 합격 (read P95 < 800ms / write P95 < 1500ms) + FR-06 Anomaly 1차 조정 | v1.1.1 머지 + staging 적용 + Open Issue #11 (staging 스펙) 해소 후 운영 데이터 1주 수집 | v1.1.1 + 운영 1주 후 14일 이내 |
| **v1.1.3 (베타)** | FR-07 베타 5팀 온보딩 + 1주 dry-run + G1~G5 측정 | 게이트 #K (TPM 2026-05-30) 해소 + v1.1.2 합격 + Open Issue #10 (n8n 가능 여부) + #12 (G2 baseline) 해소 | 위 조건 충족 후 즉시 |
| **v1.1 GA** | G1~G5 합격 시 GA 의사결정 보고서 + Production 트래픽 진입 | v1.1.3 종료 + G1~G5 합격 | v1.1.3 종료 후 7일 이내 |

---

## 다음 단계 안내

- 본 PRD 의존 외부 게이트 해소 통지 받는 즉시 `/feature` 파이프라인 진입 가능합니다.
- v1.0 의 후속이므로 신규 도메인 없음 — 대부분 `/implement` (단일 도메인 변경) 수준으로 분해됩니다.
- 단 FR-01 (실 PG 통합) + FR-05 (부하 시험) 은 다중 레포 / 다중 환경 영향이라 `/feature` 권장.

---

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-23 | v1.1 초안 작성 (7 FR / 8 Open Issue / 5 Milestone) | Claude (메인 세션 + /prd 스킬) |
