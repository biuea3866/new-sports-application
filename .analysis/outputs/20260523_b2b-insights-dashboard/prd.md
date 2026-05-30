# B2B 운영 인사이트 대시보드 확장 PRD

## 배경 (Background)

v1.0 MCP Server MVP 가 `origin/dev` 에 머지 완료된 후 (28 PR + 4 Decision A — `.feature-pipeline-state.json` v0.6 의 `completed_prs` 참조), 운영 데이터가 풍부하게 적재되고 있으나 **운영자가 어드민 UI 에서 시각화·통합 분석을 할 수단이 없습니다.**

### 본 PRD 가 의존하는 v1.0 dev 머지 결과물 (검증 가능 PR + 파일 경로)

| 의존 항목 | dev 머지 PR | 파일 / 디렉토리 (`origin/dev` 기준) |
|---|---|---|
| `mcp_audit_logs` 테이블 + Entity | BE-01b (PR #91) | `backend/src/main/kotlin/com/sportsapp/domain/mcp/McpAuditLog.kt` + V21 마이그레이션 |
| `McpAnomalyDetectedEvent` + Anomaly Domain Service | BE-08b (PR #117) | `backend/src/main/kotlin/com/sportsapp/domain/mcp/McpAnomalyDetectedEvent.kt`, `McpAnomalyDetector.kt`, `McpAnomalyDomainService.kt` |
| 어드민 MCP 페이지 3개 (`/admin/mcp/{tokens,audit-logs,docs}`) | FE-01a/01b/02/03 (PR #81/121/120/119) | `web/app/admin/mcp/{tokens,audit-logs,docs}/page.tsx` |
| 운영자 포털 (`/portal/*`) | B2B Wave3 + 후속 머지 | `web/app/portal/page.tsx` + 6 페이지 |
| `GetGoodsSalesUseCase` / `GetInventoryUseCase` / `GetTicketSalesUseCase` | BE-14 (PR #123) | `backend/src/main/kotlin/com/sportsapp/application/goods/`, `application/ticketing/` |
| `GetGuTypeStatsUseCase` (시설 구종별 통계 — **시설 가동률 % 는 미포함**, FR-03 가동률 위젯은 신규 UseCase 필요) | BE-13 (PR #122) | `application/dashboard/` 또는 `application/facility/` |
| `RefundBookingUseCase` / `IssueComplimentaryTicketUseCase` | BE-03+16 (PR #130) | `backend/src/main/kotlin/com/sportsapp/application/booking/RefundBookingUseCase.kt`, `application/ticketing/IssueComplimentaryTicketUseCase.kt` |
| `McpAuditLogAsyncRecorder` + `McpToolAuditHelper` (audit 적재) | BE-17 (PR #125), BE-17b (PR #126) | `presentation/mcp/audit/` |
| `AdminNotificationApiController` (기존 B2C 알림 발송) | (v1.0 이전부터 존재) | `backend/src/main/kotlin/com/sportsapp/presentation/notification/AdminNotificationApiController.kt` |

**검증 방법 — 리뷰어가 코드베이스에서 확인 시**: 메인 worktree 가 v1.0 머지 이전 브랜치 (`feat/qa-pipeline` 등) 일 경우 disk 에 안 보일 수 있습니다. `git ls-tree -r origin/dev --name-only | grep <파일명>` 또는 `git fetch origin && git log origin/dev --oneline | grep <PR번호>` 로 확인 가능합니다.

| 데이터 소스 | 현 상태 | 부재 |
|---|---|---|
| `mcp_audit_logs` (BE-01b 적재 중) | raw 로그 조회만 (`/admin/mcp/audit-logs`) | 시계열 집계, 토큰별 사용 TOP, 에러율, P95 latency 트렌드 |
| `McpAnomalyDetectedEvent` (BE-08b 발행 중) | Spring 내부 이벤트 — DB 영속화 없음 | 히스토리 조회, false positive 마킹, 후속 조치 추적 |
| Datadog 대시보드 (INFRA-03) | SRE 만 접근 | 운영자는 자기 데이터 못 봄 |
| B2B Wave3 stats UseCases (Facility/Goods/Inventory/Ticket) | 도메인별 분산 | 통합 KPI 페이지 부재 |
| 비정상/재고/예약 충돌/정책 위반 알림 | 발행 시점에만 통보 | 누적/검색/처리 추적 부재 |

### 운영자 실제 불편 사례

- 운영자 A: "내 MCP 토큰이 어제 1만 번 호출됐다는데 어떤 tool 인지 모름" → Datadog 못 봄 → SRE 에 티켓
- 운영자 B: "비정상 패턴 알림 받았는데 이게 실제 사고인지 false positive 인지 후속 추적 불가"
- 운영자 C: "이번 달 시설 가동률 + 굿즈 매출 + 티켓 환불율 한 화면에서 보고 싶음" → 3개 페이지 돌아다님

본 PRD 는 위 갭을 메우는 **운영자 전용 인사이트 대시보드 4 페이지** 를 추가합니다.

---

## 목표 (Goals)

- MCP 사용 분석 대시보드 — `mcp_audit_logs` 90일 시계열 + 토큰/tool 별 집계 (FR-01)
- 이상 패턴 히스토리 + 영속화 — `mcp_anomaly_events` 신규 테이블 + 운영자 false positive 마킹 (FR-02)
- 운영 통합 KPI — 시설 / 굿즈 / 티켓 / Phase 2 환불·Complimentary 한 화면 (FR-03)
- 알림 센터 — 비정상 / 재고 / 예약 / 정책 위반 push 통합 + 처리 상태 추적 (FR-04)
- 신규 BE UseCase 3종 + 집계 인프라 (FR-05)
- 운영자 본인 데이터만 노출 — IDOR 차단 (NFR 보안)
- PII 평면 노출 0건 — `PiiMasker` (BE-09) 패턴 일관 (NFR 보안)

---

## 비목표 (Non-Goals)

- 외부 BI 도구 통합 (Tableau / Looker / Metabase) — 어드민 UI 내부에서만 시각화
- 운영자별 커스텀 차트 빌더 — 정형 차트만 제공
- 비-운영자 (B2C 사용자) 노출
- Phase 2 의 `pii:unmask` scope 자동 처리 — 본 PRD 는 항상 마스킹 적용 (v1.0 정책 유지)
- v1.0 / v1.1 / v2 의 기존 기능 재정의 — 본 PRD 는 신규 시각화 추가만
- 실시간 (< 1초) 업데이트 — 1분 polling 또는 5분 batch 수용 가능
- 다국어 UI — 한국어 우선, 영어는 v1.x 후속

---

## 사용자 스토리 (User Stories)

| As a | I want to | So that |
|---|---|---|
| 운영자 | 내 MCP 토큰의 일별 호출 수 추이를 보기를 | 자동화 도구 정상 동작 + 비용 추적 |
| 운영자 | tool 별 P95 응답 시간을 보기를 | 느린 tool 발견 시 운영팀에 신고 가능 |
| 운영자 | 내 토큰의 에러율을 보기를 | 권한 부족 / scope 누락 같은 자기 설정 문제 인지 |
| 운영자 | 비정상 패턴 알림 히스토리를 보기를 | 사고/장난질/false positive 구분 가능 |
| 운영자 | anomaly 를 "false positive 로 마킹" 가능하기를 | SRE 알림 피로도 감소 |
| 운영자 | 시설 가동률 + 매출 + 티켓 환불율을 한 화면에서 보기를 | 일일 업무 5분 단축 |
| 운영자 | 알림 센터에서 미처리 알림을 카운트로 보기를 | 출근 즉시 우선순위 파악 |
| 운영팀장 | 전체 운영자의 MCP 사용 TOP 20 을 보기를 | 비용 분석 + 이상치 발견 |
| 보안 담당자 | anomaly false positive 비율을 운영자별로 보기를 | BE-08b 임계값 튜닝 데이터 확보 |
| SRE | 모든 운영자의 tool latency P95 통합을 보기를 | 운영 환경 성능 회귀 조기 발견 |

---

## 기능 요구사항 (Functional Requirements)

### FR-01. MCP 사용 분석 대시보드

**행위자**: 운영자 (어드민 또는 포털 로그인 후 인사이트 페이지 진입 — 경로 미결정 Open Issue #12).

**트리거**: 페이지 로드 + 기간 필터 변경 (1일/7일/30일/90일).

**결과**:

- **차트 1 — 일별 호출 수 추이**: 막대 또는 라인 차트. x축 = 일자, y축 = 호출 수. tool 별 색상 구분
- **차트 2 — tool 별 호출 TOP 10**: 도넛 또는 가로 막대. 운영자 본인의 tool 사용 분포
- **차트 3 — 에러율 추이**: 일별 statusCode != 2xx 비율 % 라인
- **차트 4 — P95 응답 시간 (tool 별)**: tool 별 P95 ms 막대
- **표 — 토큰별 사용 TOP 20**: 토큰 이름 / 호출 수 / 마지막 사용 / 에러율 (운영자 본인 토큰 중)
- **카운터 — RESPONSE_TOO_LARGE 발생 빈도**: 페이지 상단 알림 배지
- 기간 필터 = 1일 / 7일 / 30일 / 90일 (90일 = audit log 보관 한도)
- IDOR 차단 — `mcp_audit_logs` 의 `user_id = principal.userId` 필터 강제 (Repository 레벨)
- 운영팀장 role 보유 시 전체 운영자 데이터 토글 가능 (별도 권한 — Open Issue #1)

### FR-02. 이상 패턴 히스토리 + 영속화

**행위자**: 운영자 (이상 패턴 히스토리 페이지 진입 — 경로 미결정 Open Issue #12) + 시스템 (BE-08b 의 anomaly 이벤트 수신).

**트리거**:
- 시스템: BE-08b 의 `McpAnomalyDetectedEvent` 발행 → `@TransactionalEventListener(AFTER_COMMIT)` 으로 신규 `mcp_anomaly_events` 테이블 INSERT
- 운영자: 페이지 진입 시 본인 토큰의 anomaly 목록 조회

**결과**:

- 신규 테이블 `mcp_anomaly_events` (V26 마이그레이션 — v2 의 V26 과 충돌 가능 → V28 또는 별도 번호 — Open Issue #2):
  - id, token_id, user_id, tool_name, detected_at, baseline_avg, current_count, spike_ratio, status (`OPEN` / `RESOLVED` / `FALSE_POSITIVE`), resolved_at, resolved_by, note (audit 6 컬럼)
- 보관 기간 180일 — `deleted_at` soft-delete 후 cron 정리 (Open Issue #3)
- 운영자가 anomaly row 클릭 → 상세 모달 (호출 시점 audit log 5건 미리보기 + scope/tool 정보)
- 운영자가 "false positive 마킹" 버튼 클릭 → status = FALSE_POSITIVE + note 입력 (50자)
- 운영자가 "처리 완료" 버튼 클릭 → status = RESOLVED + note 입력
- 운영팀장은 본인 + 관리 운영자의 anomaly 모두 조회 (Open Issue #1 권한과 연동)
- FR-06 의 false positive 비율 데이터 입력 — BE-08b 임계값 조정 (v1.1 FR-06) 의 근거 자료

### FR-03. 운영 통합 KPI 대시보드

**행위자**: 운영자 (`/admin/dashboard` 페이지 확장 — 기존 페이지 위에 KPI 위젯 추가).

**트리거**: 페이지 로드 + 기간 필터 변경 (오늘 / 이번 주 / 이번 달 / 사용자 지정).

**결과**:

**위젯 — 시설 영역**
- 일별 가동률 % (예약 시간 / 영업 시간)
- 노쇼율 % (마지막 30일)
- 가장 인기 시설 TOP 5

**위젯 — 굿즈 영역**
- 일별 매출 (KRW) 추이
- 재고 회전율 (월간 매출 / 평균 재고)
- 품절 SKU 카운트

**위젯 — 티켓 영역**
- 일별 판매 카운트
- 환불율 % (`refundBooking` v1.0 의 cancel + Phase 2 의 refund 통합)
- Complimentary 발급 카운트 (Phase 2)

**위젯 — Phase 2 운영 통합**
- `RefundBookingUseCase` 호출 카운트 (성공 / 실패 / DLQ 적재)
- `IssueComplimentaryTicketUseCase` 호출 카운트

**데이터 소스 (v1.0 dev 머지 결과물 — 모두 존재 확인)**:
- BE-13/14 의 stats UseCases: `GetFacilityStatsUseCase`, `GetGoodsSalesUseCase`, `GetInventoryUseCase`, `GetTicketSalesUseCase` (dev `application/goods/`, `application/ticketing/`)
- Phase 2 (BE-03+16 머지됨) 의 `RefundBookingUseCase`, `IssueComplimentaryTicketUseCase`
- 운영자 본인 owner_user_id 필터 강제

**적용 페이지**:
- 현재 `web/app/admin/dashboard` 가 미존재 — 운영자 대시보드는 `web/app/portal/page.tsx` (사업자 포털)
- **결정 (Open Issue #12 신규)**: (A) `web/app/portal/page.tsx` 에 KPI 위젯 4영역 직접 추가 vs (B) `web/app/portal/insights/page.tsx` 신규 페이지로 분리 + 사이드바 링크. 결정 전까지 (B) 가정.

**기간 필터 + drill-down**: 위젯 클릭 → 해당 도메인 상세 페이지 (`/portal/facilities` 등 — 기존 운영자 포털 페이지)

### FR-04. 알림 센터

**행위자**: 운영자 (알림 센터 페이지 + 상단 종 아이콘 — 경로 미결정 Open Issue #12).

**트리거**: 다음 이벤트 발생 시 알림 생성:
- BE-08b anomaly detected → `mcp_anomaly_events` 통해 알림 (FR-02 연동)
- 재고 부족 (Open Issue #4 — 재고 임계값 알림 신규 신설 vs 기존 알림 통합)
- 시설 예약 충돌 (`BookingConflictEvent` — 도메인 이벤트 신설 또는 기존 활용)
- 정책 위반 시도 (`@PreAuthorize` 거부 + paramsHash mismatch 등 — audit log statusCode 403/400 분석)
- v2 의 자동화 실패 (DLQ 적재) — v2 의 FR-07 알림 채널과 통합 (Open Issue #5)

**결과**:

- 신규 테이블 `operator_inbox_notifications` (V29 마이그레이션 — v2 의 V26~V28 다음. 기존 `notifications` 테이블 + 기존 `AdminNotificationApiController` 와 명칭 충돌 회피 — Open Issue #13 으로 최종 명칭 확정):
  - id, recipient_user_id, type (`ANOMALY` / `LOW_INVENTORY` / `BOOKING_CONFLICT` / `POLICY_VIOLATION` / `AUTOMATION_FAILURE`), title, body, link, status (`UNREAD` / `READ` / `ARCHIVED`), **`read_at` DATETIME(6) NULL** (GA 메트릭 측정용), created_at (audit 6컬럼)
- 보관 30일 → 자동 archive
- 어드민 상단 종 아이콘 — 미읽음 카운트 badge (실시간 polling 1분)
- 페이지 진입 시 미읽음 → 읽음 자동 전이 (또는 클릭 시 — Open Issue #6). 전이 시 `read_at = NOW(6)` 갱신.
- 알림 타입별 필터 + 검색 + 페이징
- 운영자 본인 알림만 노출 (IDOR 차단)
- 채널 (in-app only vs Slack/Email 동시 발송) — v2 FR-04 와 통합 시 Open Issue #5 결정
- **기존 `AdminNotificationApiController` (이미 존재 — `POST /admin/notifications/send` B2C 알림 발송용)** 와 신규 운영자 inbox API 구분: 본 PRD 는 `OperatorInboxApiController` 신규로 분리. 기존 컨트롤러 수정 0.

### FR-05. 신규 BE UseCase 3종 + 집계 인프라

**행위자**: BE 시스템 — FR-01/02/03/04 의 데이터 공급.

**트리거**: FE 가 BFF Route Handler 통해 호출.

**결과**:

**UseCase 4종 (FR-03 시설 가동률 추가로 3 → 4)**
- `ListMcpAuditUsageStatsUseCase` — FR-01 데이터 공급. 기간 + 운영자 필터 + 시계열 집계 (일별 호출 수 / 에러율 / P95)
- `ListMcpAnomaliesUseCase` — FR-02 데이터 공급. 운영자 + 기간 + status 필터 + 페이징
- `GetOperationalKpisUseCase` — FR-03 통합 KPI. 굿즈/티켓 stats UseCase 조합 + 환불/Complimentary 카운트 (Open Issue #22 — 신규 vs `GetMyDashboardSummaryUseCase` 확장)
- **`GetFacilityUtilizationUseCase` (신규)** — FR-03 시설 가동률 % (예약 시간 / 영업 시간) 집계. dev 의 `GetGuTypeStatsUseCase` 는 구종별 카운트만 — 가동률 계산 불가하므로 신규. 의존: `BookingRepository` 의 슬롯/예약 데이터

**집계 인프라 (Open Issue #7 결정)**:

| 옵션 | 동작 | 장점 | 단점 |
|---|---|---|---|
| **A. Materialized View (MySQL)** | 일별 집계 view 1분 갱신 | 단순, 추가 인프라 0 | MySQL refresh 부하 (audit log 90일 row 수 검증 필요) |
| **B. ClickHouse 신규 도입** | audit log 를 ClickHouse 미러링 후 ClickHouse 쿼리 | OLAP 최적, 수 초 응답 | 신규 인프라, 운영 부담, MySQL 동기화 지연 |
| **C. Application batch** | Spring `@Scheduled` 5분 batch 가 `mcp_audit_daily_stats` 테이블 적재 | 단순, MySQL 만 | batch 실패 시 데이터 갭 |

각 옵션의 PoC + 부하 시험 결과 비교 후 결정.

**시계열 보관 정책**:
- raw audit log: 90일 (v1.0 기존)
- anomaly event: 180일 (FR-02)
- 알림: 30일 (FR-04)
- 일별 집계 view (옵션 A/C): 365일

---

## 비기능 요구사항 (Non-Functional Requirements)

| 분류 | 항목 | 기준 |
|---|---|---|
| 성능 | 대시보드 페이지 첫 로드 P95 | < 2초 |
| 성능 | 집계 쿼리 P95 | < 500ms (옵션 A/C) / < 100ms (옵션 B) |
| 성능 | 1 운영자당 audit log 90일 row 수 | 평균 100만건 가정 → 집계 인프라 부하 시험 필수 |
| 보안 | IDOR 차단 — 운영자 본인 데이터만 | 100% (Repository where 절 + 단위 테스트) |
| 보안 | PII 평문 노출 0건 | `paramsMasked` 만 사용 — 평문 컬럼 접근 0 |
| 보안 | 운영팀장 권한 (전체 데이터 조회) | 별도 role / scope (Open Issue #1) |
| 신뢰성 | anomaly event 영속화 누락 | 0건 (`@TransactionalEventListener(AFTER_COMMIT)` + 단위 테스트) |
| 신뢰성 | 알림 누락 | 0건 (event source 별 단위 테스트) |
| 신뢰성 | 집계 갱신 지연 | < 5분 (배치 옵션) / 즉시 (Materialized View) |
| 운영 | 1분 polling (어드민 종 아이콘) | 0.5초 이내 응답 |
| 회귀 | v1.0 / v1.1 / v2 기능 변화 | 0건 |

---

## 제약 조건 (Constraints)

- **차트 라이브러리 미결정 (Open Issue #8)** — Recharts / Apache ECharts / Chart.js / Visx 중 결정 필요. FE 번들 사이즈 + 접근성 + 한국어 폰트 지원 검토
- **집계 인프라 미결정 (Open Issue #7)** — Materialized View / ClickHouse / Application batch 중 결정. 옵션별 PoC + 부하 시험 합격 후 채택
- **v2 마이그레이션 번호 충돌** — v2 의 V26 (mcp_webhooks) / V27 (mcp_scheduled_jobs) / V28 (automation scopes) 와 본 PRD 의 V?? 번호 조율 필요 (Open Issue #2)
- **v2 알림 채널 통합** — v2 의 FR-04 자동화 80% rate 알림 + FR-07 DLQ 알림이 본 PRD 의 알림 센터로 통합되는지 결정 (Open Issue #5)
- **운영팀장 role 부재** — 현재 코드베이스에 "운영팀장" 권한 정의 없음 → 신규 role 또는 scope 신설 필요 (Open Issue #1)
- **하위 호환성** — Open Issue #12 결정에 따라:
  - (A) `/portal/page.tsx` 직접 수정 → 기존 위젯 동작 변화 0 보장 필수 (회귀 테스트)
  - (B) `/portal/insights` 또는 `/admin/...` 신규 페이지 → 신규이므로 하위 호환 해당 없음 (기존 페이지 무수정)
- **데이터 보존 정책 — Legal 검토 필요** — 30일/90일/180일/365일 보관이 GDPR / 개인정보보호법 충족 여부 (Open Issue #9)
- **v1.0 의 `params_masked` 컬럼 의존** — 인사이트 페이지가 raw params 접근 시도 0 — `params_masked` 만 사용

---

## 영향 범위 (Scope)

| 레포 / 디렉토리 | 변경 유형 | 설명 |
|---|---|---|
| `backend/src/main/kotlin/com/sportsapp/domain/mcp/McpAnomalyEvent.kt` | 신규 | anomaly 영속화 Entity |
| `backend/src/main/kotlin/com/sportsapp/domain/mcp/McpAnomalyEventRepository.kt` | 신규 | Repository interface |
| `backend/src/main/kotlin/com/sportsapp/domain/{notification|admin|mcp}/OperatorInboxNotification.kt` | 신규 | 운영자 inbox 알림 Entity. **패키지 위치 미결정** — Open Issue #14. 기존 B2C `Notification.kt` 의 `NotificationStatus` enum (`QUEUED`/`SENT`/`FAILED`) 과 본 PRD 의 status (`UNREAD`/`READ`/`ARCHIVED`) 충돌 → 같은 패키지 사용 시 enum 분리 강제. 권장: 별도 패키지 (`domain/admin/inbox/` 또는 `domain/mcp/notification/`) |
| `backend/src/main/kotlin/com/sportsapp/application/mcp/ListMcpAuditUsageStatsUseCase.kt` | 신규 | FR-01 |
| `backend/src/main/kotlin/com/sportsapp/application/mcp/ListMcpAnomaliesUseCase.kt` | 신규 | FR-02 |
| `backend/src/main/kotlin/com/sportsapp/application/dashboard/GetOperationalKpisUseCase.kt` 또는 기존 `GetMyDashboardSummaryUseCase.kt` 확장 | 신규 또는 수정 | FR-03 통합. **기존 `GetMyDashboardSummaryUseCase` (operator 대시보드 요약) 와 책임 중복 가능성** — Open Issue #22 신규 (신규 vs 확장 결정) |
| `backend/src/main/kotlin/com/sportsapp/application/notification/ListAdminNotificationsUseCase.kt` | 신규 | FR-04 |
| `backend/src/main/kotlin/com/sportsapp/presentation/mcp/admin/McpInsightsAdminApiController.kt` | 신규 | FR-01/02 admin API |
| `backend/src/main/kotlin/com/sportsapp/presentation/notification/OperatorInboxApiController.kt` | 신규 | FR-04 운영자 inbox API (기존 `AdminNotificationApiController` 는 그대로 유지 — `POST /admin/notifications/send` B2C 알림 발송용. 본 PRD 는 별도 컨트롤러 신규) |
| `backend/src/main/kotlin/com/sportsapp/presentation/notification/McpAnomalyEventPersister.kt` | 신규 | `@TransactionalEventListener(AFTER_COMMIT)` — BE-08b 이벤트 영속화 |
| `backend/src/main/kotlin/com/sportsapp/infrastructure/persistence/mcp/McpAuditDailyStatsRepository.kt` | 신규 (옵션 C 선택 시) | 일별 집계 batch 적재 |
| `backend/src/main/resources/db/migration/V??__create_mcp_anomaly_events.sql` | 신규 | (번호는 v2 와 조율 — Open Issue #2) |
| `backend/src/main/resources/db/migration/V??__create_admin_notifications.sql` | 신규 | (번호는 v2 와 조율) |
| `backend/src/main/resources/db/migration/V??__create_mcp_audit_daily_stats.sql` | 신규 (옵션 A/C 선택 시) | 일별 집계 view 또는 테이블 |
| `web/app/{admin|portal}/mcp/insights/page.tsx` | 신규 | FR-01 페이지. **경로 미결정** — Open Issue #12 (모든 4페이지 일관). 어드민 선택 시 기존 `/admin/mcp/{tokens,audit-logs,docs}` 와 같은 위치 |
| `web/app/{admin|portal}/mcp/anomalies/page.tsx` | 신규 | FR-02 페이지 — Open Issue #12 결정 적용 |
| `web/app/portal/insights/page.tsx` 또는 `web/app/portal/page.tsx` | 신규 / 수정 | FR-03 KPI 위젯 — Open Issue #12 결정 (B 옵션 신규 vs A 옵션 portal/page 직접 수정) |
| `web/app/portal/notifications/page.tsx` 또는 `web/app/admin/notifications/page.tsx` | 신규 | FR-04 페이지 — 운영자 포털 측면 (Open Issue #12 와 일관) |
| `web/app/_components/NotificationBell.tsx` 또는 `web/app/portal/_components/NotificationBell.tsx` | 신규 | 운영자 포털/어드민 공통 종 아이콘 — 레이아웃 위치 결정 (Open Issue #12) |
| `web/app/api/admin/mcp/insights/route.ts` | 신규 | BFF (FR-01) |
| `web/app/api/admin/mcp/anomalies/route.ts` | 신규 | BFF (FR-02) |
| `web/app/api/portal/notifications/route.ts` 또는 `web/app/api/admin/notifications/route.ts` | 신규 | BFF (FR-04) — Open Issue #12 결정 |
| `web/lib/admin/charts.ts` | 신규 | 차트 라이브러리 wrapper (Open Issue #8 결정 후) |
| `web/package.json` | 수정 | 차트 라이브러리 dependency 추가 |

---

## 오픈 이슈 (Open Issues)

기한은 절대 날짜 또는 마일스톤 진입 절대 기한입니다.

| # | 질문 | 담당 | 기한 |
|---|---|---|---|
| 1 | ~~"운영팀장" 권한 신설~~ | ~~보안 + PM~~ | **해소 (2026-05-23) — 신규 `OPERATIONS_MANAGER` role 신설 + V?? 시드: .any scope 6건 (facility/booking/goods/ticketing/notification/operator.profile.any) + `admin:operator:read` 신규 권한 할당. ADMIN role 과 분리** |
| 2 | ~~v2 마이그레이션 번호 충돌~~ | ~~BE + DBA~~ | **해소 (2026-05-23) — origin/dev V24 기준: v1.1 V25 / v2 V26~V28 / 본 PRD V29 (mcp_anomaly_events) + V30 (operator_inbox_notifications)** |
| 3 | anomaly event 보관 기간 180일 — Legal 검토 필요 (Open Issue #9 와 통합 가능) [외부 의존 — Legal, #9 와 통합] | Legal | **2026-06-30** |
| 4 | ~~재고 부족 알림 source~~ | ~~BE + PM~~ | **해소 (2026-05-23) — `LowInventoryEvent` 신규 도메인 이벤트. Kafka 토픽 (v2 #2 와 동일 인프라 = `KafkaDomainEventPublisher` 재사용). #20 과 통합** |
| 5 | ~~v2 알림 채널 통합~~ | ~~PM + 운영팀~~ | **해소 (2026-05-23) — v2 FR-04/FR-07 알림을 `operator_inbox_notifications` 로 통합. 단일 알림 서테 + SSE 인프라 공유** |
| 6 | ~~알림 읽음 처리~~ | ~~UX + PM~~ | **해소 (2026-05-23) — 항목 클릭 시만 read. 명시적 사용자 의도. 페이지 진입 시 일괄 read 금지** |
| 7 | 집계 인프라 — Materialized View / ClickHouse / Application batch — 옵션별 PoC + 부하 시험 결과 비교 후 결정 [외부 의존 — DevOps + QA PoC 필요] | BE + DevOps + QA | **2026-08-04** |
| 8 | ~~차트 라이브러리~~ | ~~FE + UX~~ | **해소 (2026-05-23) — Recharts. React 표준, 번들 ~93KB, 접근성/한국어 폰트 OK, KPI 4종 + 시계열 차트 충분** |
| 9 | 데이터 보존 정책 — GDPR / 개인정보보호법 검토 + 운영자 본인 삭제 요청 시 anomaly/알림/audit log 처리 [외부 의존 — Legal, #3 과 통합] | Legal | **2026-06-30** |
| 10 | `mcp_audit_logs` 90일 row 수 측정 — 1 운영자당 평균 / 최대 / 95퍼센타일 [외부 의존 — 운영 데이터 수집 후. 코드 조사 추정 = 2.25M row] | 운영팀 + SRE | **2026-07-14** |
| 11 | ~~PII 마스킹 정책~~ | ~~보안 + Legal~~ | **해소 (2026-05-23) — 본인은 unmask + 타인은 마스킹 적용. 최소 권한 원칙** |
| 12 | ~~FR-01/02/03/04 FE 경로~~ | ~~UX + PM + FE~~ | **해소 (2026-05-23) — (A) `web/app/portal/*` 하위 일괄. /portal/insights, /portal/anomalies, /portal/page (KPI 위젯), /portal/notifications. 운영자 본인 데이터 시각화는 포털이 자연** |
| 13 | ~~알림 테이블명~~ | ~~BE + DBA~~ | **해소 (2026-05-23) — `operator_inbox_notifications`. 도메인 분리 명확, 기존 컨트롤러 명칭 충돌 회피** |
| 14 | ~~알림 Entity 패키지~~ | ~~BE~~ | **해소 (2026-05-23) — `domain/operator/inbox/OperatorInboxNotification.kt`. B2C Notification 과 enum (`UNREAD`/`READ`/`ARCHIVED` vs `QUEUED`/`SENT`/`FAILED`) 충돌 회피** |
| 15 | ~~ADMIN role 권한 보유 여부~~ | ~~보안~~ | **해소 (2026-05-23) — ADMIN role 은 V2 시드되어 있으나 `.any` MCP scope 매핑 0건. 신규 OPERATIONS_MANAGER role 필수 (Issue #1 과 통합)** |
| 16 | P95 latency 집계 계산 방법 — MySQL 8.0 `PERCENTILE_CONT` vs application 정렬 vs 사전 batch 집계 — 옵션 A/B/C 각각 비교 [외부 의존 — #7 PoC 와 동시] | BE + DBA | **2026-08-04** |
| 17 | ~~V23/V24 마이그레이션 소유권~~ | ~~DBA + 전체 트랙~~ | **해소 (2026-05-23) — V23 = mcp facility stats / V24 = mcp Phase 2 permissions. 공백 없음. Issue #2 와 통합 처리** |
| 18 | ~~FE analytics 인프라~~ | ~~v2 와 통합~~ | **해소 (2026-05-23) — Mixpanel / GA4 / Datadog RUM 0건. GA 메트릭 5번째 지표 (어드민 클릭 -30%) 는 v1.0.0 비목표 분리, v2 GA 시점에 BFF 서버 로그 우회 또는 신규 도입 결정** |
| 19 | ~~anomaly false positive 마킹 동시성~~ | ~~BE~~ | **해소 (2026-05-23) — `@Version` 낙관락. 후행 커밋 OptimisticLockException, v1.0 패턴 일치** |
| 20 | ~~LowInventoryEvent / BookingConflictEvent 신설~~ | ~~BE + PM~~ | **해소 (2026-05-23) — 신규 도메인 이벤트 2종 정의. Kafka 토픽 (v2 #2 `KafkaDomainEventPublisher` 재사용). #4 와 통합** |
| 21 | ~~알림 polling vs SSE~~ | ~~FE + BE~~ | **해소 (2026-05-23) — SSE (어드민/포털 연결). nginx SSE 설정 v1.0 이미 적용. v2 DLQ 실시간 알림 통합 시 즉시 지원** |
| 22 | ~~GetOperationalKpisUseCase 신규 vs 확장~~ | ~~BE + PM~~ | **해소 (2026-05-23) — 신규 `GetOperationalKpisUseCase`. 기존 `GetMyDashboardSummaryUseCase` 는 운영자 계정 요약 전용 유지. 책임 분리** |
| 23 | ~~FR-03 BFF route~~ | ~~FE~~ | **해소 (2026-05-23) — 신규 `/api/portal/insights/route.ts`. #12 (A) + #22 (신규 UseCase) 와 일관** |

---

## 마일스톤

본 PRD 는 v2 와 별도 트랙입니다. v1.0/v1.1 머지 후, v2 와 병렬 진행 가능합니다.

| 단계 | 내용 | 진입 조건 | 기한 |
|---|---|---|---|
| **v1.0.0 (인사이트 v1)** | FR-01 (MCP 사용 분석) + FR-05 의 `ListMcpAuditUsageStatsUseCase` + 집계 옵션 PoC 1차 | Open Issue #1 + #2 + #8 + #9 + #10 + #11 + #12 (FE 경로 결정) + #15 (ADMIN role 재사용) + #17 (V23/V24 소유권) 해소 | 위 9건 해소 후 21일 이내 |
| **v1.1.0 (anomaly 영속화)** | FR-02 (이상 패턴 히스토리) + `mcp_anomaly_events` 마이그레이션 + `McpAnomalyEventPersister` (v1.0 BE-08b 의 `McpAnomalyDetectedEvent` 이벤트 영속화) | Open Issue #3 + #4 + #6 + #13 (테이블명) + #14 (패키지 위치) + #19 (동시성) 해소 + v1.0.0 머지 | 위 7건 충족 후 21일 이내 |
| **v1.2.0 (KPI 통합 + 알림 센터)** | FR-03 (운영 통합 KPI — BE-13/14/BE-03+16 기존 UseCase 활용 + 시설 가동률 신규 UseCase) + FR-04 (알림 센터) + 집계 인프라 최종 결정 (옵션 A/B/C) | Open Issue #5 + #7 + #16 (P95 계산) + #20 (이벤트 신설) + #22 (UseCase 신규/확장) + #23 (BFF route 신규/확장) 해소 + v1.1.0 머지 + v2.0.1 머지 | 위 8건 충족 후 28일 이내 |
| **v1.3.0 (성능 합격 + GA)** | 부하 시험 (운영자 30명 동시 + 90일 시계열 쿼리) + 대시보드 P95 < 2초 합격 + 옵션 C batch 실패 시 DLQ 정책 명세 | v1.2.0 머지 + staging 부하 환경 + Open Issue #21 (polling vs SSE) 해소 | v1.2.0 머지 후 14일 이내 |
| **v1 GA** | 베타 운영자 5팀 dry-run + 사용 시간 측정 (G-인사이트 메트릭 합격) | v1.3.0 합격 + Open Issue #18 (FE analytics 인프라 — v2 와 통합) 해소 | v1.3.0 합격 + #18 해소 후 14일 |

### GA 진입 메트릭 (G-인사이트)

| 지표 | 목표 | 측정 방법 |
|---|---|---|
| 운영자별 인사이트 페이지 일 방문 | > 1회/일 (베타 5팀 평균) | FE analytics (v2 Open Issue #18 와 통합 — 인프라 확보 시 활성화. 미확보 시 server-side route handler 호출 카운트로 대체) |
| 대시보드 페이지 P95 응답 시간 | < 2초 | Datadog RUM 또는 server-side timing |
| anomaly false positive 마킹 비율 | 측정만 (베이스라인 수립) | `mcp_anomaly_events.status = FALSE_POSITIVE` / 전체 비율 — v1.1 FR-06 anomaly 임계값 조정 입력 자료 |
| 알림 미처리 평균 시간 (생성 → READ) | < 4시간 (업무시간 기준) | `operator_inbox_notifications.created_at` vs `read_at` (FR-04 의 `read_at` 컬럼 활용) |
| 어드민 클릭 수 감소 (KPI 통합 효과) | -30% (베타 운영자 1주 전/후) | v1.1 FR-07 G2 baseline 인프라 활용 — Open Issue #18 (FE analytics) 해소 시 활성화. 미확보 시 본 지표는 GA 비차단 (측정 보류) |

---

## 다음 단계 안내

- 본 PRD 의 Open Issue **23건** 중 **차트 라이브러리 (#8) + 집계 인프라 (#7) + FE 경로 결정 (#12) + 운영팀장 권한 (#1) + UseCase 중복 (#22)** 이 핵심 차단 요소입니다.
- v1.1 / v2 와 병행 가능 — 단 마이그레이션 번호 충돌 (#2) + v2 알림 채널 통합 (#5) 은 조율 필수.
- 차단 해소 후 `/feature` 파이프라인 진입 권장 (BE 신규 UseCase 3종 + FE 4 페이지 + 마이그레이션 3건 — 다중 도메인).
- FR-04 의 BookingConflictEvent / LowInventoryEvent 가 도메인 이벤트로 신설된다면 Kafka 토픽 신규 가능성 — `/feature` 진입 시 TPM 검토.

---

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-23 | v1.0 초안 작성 (5 FR / 11 NFR / 11 Open Issue / 4+1 Milestone + G-인사이트 5 지표) | Claude (메인 세션 + /prd 스킬) |
| 2026-05-23 | 1차 prd-reviewer 반영 — Open Issue 11 → 21 확장, FE 경로 미결정 명시, OperatorInboxNotification 분리, `read_at` 추가, AdminNotificationApiController 충돌 회피 | Claude (수정) |
| 2026-05-23 | 2차 prd-reviewer 반영 — v1.0 dev 머지 PR SHA 명기, Issue #22/#23 추가 (UseCase + BFF route 중복), 패키지 위치 충돌 enum 분리 명시. 총 Open Issue 23건 | Claude (수정) |
