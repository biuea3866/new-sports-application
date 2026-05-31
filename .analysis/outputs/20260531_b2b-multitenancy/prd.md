# B2B SaaS 멀티테넌시 (Multi-Tenancy) PRD

> 작성일: 2026-05-31
> 작성자: biuea3866@gmail.com
> 소스: C1 비즈니스 확장 아이디어 — 운영자 포털 + MCP를 조직 단위로 격리·구독 판매
> 관련 PRD: [AI 운영 어시스턴트](../20260531_ai-ops-assistant/prd.md), [MCP v2 자동화](../20260523_mcp-server-v2-automation/prd.md)

---

## 진행 현황 (Status Ledger)

> 최종 갱신: 2026-05-31 | 전체: 0/7 배포완료 (0%)

| FR | 기능 | 상태 | 구현(PR) | 배포 | 비고 |
|----|------|------|---------|------|------|
| FR-01 | Tenant(조직) 도메인 + 멤버십 | ⬜ 대기 | — | — | 전 FR 선행 |
| FR-02 | 데이터 격리 (tenant_id 전파 + 필터) | ⬜ 대기 | — | — | FR-01 선행, 최대 리스크 |
| FR-03 | 테넌트 컨텍스트 미들웨어 (JWT claim) | ⬜ 대기 | — | — | FR-01 선행 |
| FR-04 | 테넌트 온보딩 + 멤버 초대 | ⬜ 대기 | — | — | FR-01 선행 |
| FR-05 | 플랜/구독 과금 (Stripe-style billing) | ⬜ 대기 | — | — | FR-01, 구독 PRD 연계 |
| FR-06 | 테넌트별 MCP 토큰·webhook 격리 | ⬜ 대기 | — | — | FR-02 선행 |
| FR-07 | 슈퍼어드민 콘솔 (테넌트 관리) | ⬜ 대기 | — | — | FR-01 선행 |

### 상태 정의 (증거 기반 — `rules/COMPLETION-RULE.md` 연계)

| 상태 | 진입 조건 (증거) |
|---|---|
| ⬜ 대기 | 티켓만 존재 |
| 🟡 진행중 | 브랜치 존재 / 커밋 시작 |
| 🔵 구현완료 | dev 머지 PR + COMPLETION-RULE §1~4 충족 (테스트 통과 아티팩트) |
| ✅ 배포완료 | release 태그 / 배포 로그 첨부 |

---

## 배경 (Background)

현재 B2B 운영자 기능(포털·MCP·인사이트)은 **개별 사용자(`ownerId` / `operatorId`) 단위**로 동작합니다. 조직(시설 체인, 구단, 지자체) 개념이 없어 다음이 불가능합니다.

### 현 구조의 한계 — "조직"이 1급 시민이 아님

| 도메인 | 현 소유권 | 한계 |
|---|---|---|
| `Slot` | `ownerId: Long` (개별 user) | 시설 체인의 여러 매니저가 같은 시설 공동 운영 불가 |
| `Event` | `ownerId: Long` | 구단 직원 여러 명이 한 구단 경기 공동 관리 불가 |
| `Product` | `ownerId: Long` | 브랜드 판매팀 권한 분리 불가 |
| `McpToken` | `userId` 발급 | 조직 단위 토큰·rate limit·정산 불가 |
| 인사이트 KPI | 개인 데이터 집계 | "우리 조직 전체 매출" 뷰 부재 |

→ 결과: **B2B를 구독 SaaS로 판매할 단위(조직 = 과금·격리 경계)가 없습니다.** 지금은 1인 운영자 도구입니다.

### 비즈니스 기회

| 모델 | 설명 |
|---|---|
| 조직 구독 | 시설 체인·구단·지자체에 월정액 포털 판매 (시트 수·기능 tier별) |
| 멤버 시트 과금 | 조직 내 운영자 N명 단위 과금 |
| 기능 tier | 인사이트·MCP 자동화·AI 어시스턴트를 상위 tier로 분리 판매 |

본 PRD는 **조직(Tenant)을 1급 도메인으로 도입**하고, 기존 모든 운영 데이터를 테넌트 경계로 격리하며, 구독 과금을 붙입니다.

---

## 목표 (Goals)

- Tenant(조직) 엔티티 + 멤버십(역할: OWNER / MANAGER / VIEWER)을 도입한다 (FR-01)
- 모든 운영 데이터(slot/event/product/booking/payment/mcp)에 `tenant_id`를 전파하고 조회를 테넌트로 강제 필터한다 (FR-02)
- JWT에 tenant claim을 실어 요청마다 테넌트 컨텍스트를 확정한다 (FR-03)
- 테넌트 셀프 온보딩 + 멤버 초대 플로우를 제공한다 (FR-04)
- 플랜(Free / Pro / Enterprise)별 구독 과금 + 시트·기능 제한을 적용한다 (FR-05)
- MCP 토큰·webhook을 테넌트 단위로 격리한다 (FR-06)
- 슈퍼어드민이 전체 테넌트를 관리·정지·과금 조회한다 (FR-07)

### 측정 가능 목표 (KPI)

| 지표 | 현재 | 목표 |
|---|---|---|
| 과금 가능 단위 | 없음 (개인) | 조직(테넌트) |
| 데이터 cross-tenant 누수 | N/A | 0건 (격리 테스트 강제) |
| 신규 조직 온보딩 시간 | N/A (수동) | 셀프 10분 이내 |

---

## 비목표 (Non-Goals)

- **물리적 DB 분리** — 단일 DB + `tenant_id` 행 격리(shared schema)로 시작. schema-per-tenant·DB-per-tenant는 규모 도달 후 별도 PRD
- **B2C 사용자 영향** — 일반 사용자(USER role)는 테넌트 무관, 기존 플로우 100% 유지
- **기존 데이터 대규모 재설계** — `tenant_id` 컬럼 추가 + 백필 마이그레이션으로 점진 적용
- **실 PG 빌링 연동 깊이** — 과금 로직은 구독 PRD(B2)와 공유, 본 PRD는 테넌트 경계에 집중

---

## 사용자 스토리 (User Stories)

| As a | I want to | So that |
|---|---|---|
| 시설 체인 대표 | 조직을 만들고 매니저 5명을 초대 | 직원들과 운영 분담 |
| 조직 매니저 | 우리 조직 시설·경기·상품만 보고 관리 | 타 조직 데이터 노출 없음 |
| 조직 대표 | 조직 전체 매출·가동률을 한 화면에서 | 개인별로 안 돌아다님 |
| 조직 대표 | Pro 플랜 구독해 AI 어시스턴트 기능 사용 | 상위 기능 이용 |
| 보안 담당 | 우리 조직 MCP 토큰이 타 조직과 격리 | 데이터 사고 방지 |
| 플랫폼 슈퍼어드민 | 미납 조직을 정지 | 과금 통제 |
| 플랫폼 슈퍼어드민 | 조직별 사용량·과금 현황 조회 | 운영 |

---

## 기능 요구사항 (Functional Requirements)

### FR-01. Tenant(조직) 도메인 + 멤버십

**결과**:
- `Tenant` 엔티티 (name, slug, plan, status: ACTIVE/SUSPENDED, audit 6)
- `TenantMembership` 매핑 엔티티 (**@ManyToMany 금지 — 독립 엔티티**): tenant_id, user_id, role(OWNER/MANAGER/VIEWER), granted_by, active
- 한 user는 복수 테넌트 소속 가능 (다중 조직)
- 역할별 권한: OWNER(과금·멤버관리), MANAGER(운영), VIEWER(조회만)

### FR-02. 데이터 격리 (tenant_id 전파 + 강제 필터) — **최대 리스크**

**결과**:
- 운영 엔티티(`Slot`, `Event`, `Product`, `Booking`, `Payment`, `McpToken`, `OperatorInboxNotification`)에 `tenant_id` 컬럼 추가
- 기존 `ownerId`는 "생성자"로 유지, `tenant_id`가 "소유 조직"
- **모든 Repository 조회에 `tenant_id = :currentTenant` 강제** — QueryDSL CustomRepository에 테넌트 필터 baseline 적용 (soft-delete `deleted_at IS NULL`과 동일 패턴)
- cross-tenant 접근 시 404 (존재 은닉) 또는 403
- 기존 데이터 백필: `ownerId` → 개인 테넌트 자동 생성 후 매핑 (마이그레이션)

<details>
<summary>격리 강제 방식 (초안)</summary>

- Hibernate `@Filter` + 세션별 `enableFilter("tenant", tenant_id)` 또는
- QueryDSL baseline predicate를 BaseRepositoryImpl에 주입
- 테스트: 테넌트 A 컨텍스트로 테넌트 B 데이터 조회 시 0행 반환을 모든 Repository에서 검증 (격리 회귀 스위트)

</details>

### FR-03. 테넌트 컨텍스트 미들웨어 (JWT claim)

**결과**:
- 로그인 시 소속 테넌트 목록 반환, 활성 테넌트 선택 → JWT에 `tenant_id` claim 적재
- 요청마다 `TenantContext`(ThreadLocal/Request scope)에 tenant_id 확정
- 테넌트 미선택·미소속 요청은 운영 API 403
- 테넌트 전환 API (소속 조직 간 스위치)

### FR-04. 테넌트 온보딩 + 멤버 초대

**결과**:
- 셀프 조직 생성 (이름·slug) → 생성자 OWNER 자동 부여 + 개인 데이터 마이그레이션 옵션
- 이메일 초대 → 초대 수락 시 멤버십 생성 (만료 토큰, 역할 사전 지정)
- 멤버 역할 변경·제거 (OWNER만)

### FR-05. 플랜/구독 과금

**결과**:
- 플랜 정의: Free(시트 1, 인사이트 ✕) / Pro(시트 10, 인사이트·MCP ✓) / Enterprise(무제한, AI 어시스턴트 ✓)
- 플랜별 기능 게이트 (feature flag) + 시트 수 제한 enforcement
- 월 구독 과금 — **구독 PRD(B2)의 정기결제 엔진 공유**
- 미납 시 SUSPENDED → 조회만 허용, 쓰기 차단

> 과금 상세(빌링키·정기결제·실패 재시도)는 [구독/멤버십 PRD](../20260531_subscription-membership/prd.md)와 엔진 공유.

### FR-06. 테넌트별 MCP 토큰·webhook 격리

**결과**:
- MCP 토큰 발급 시 `tenant_id` 귀속, rate limit·audit·인사이트를 테넌트 단위 집계
- webhook(MCP v2)도 테넌트 격리
- 테넌트 정지 시 소속 토큰 일괄 비활성

### FR-07. 슈퍼어드민 콘솔

**결과**:
- 전체 테넌트 목록·검색·상태(활성/정지)·플랜·사용량 조회
- 강제 정지/해제, 플랜 수동 조정
- 테넌트별 과금·MAU·MCP 호출량 대시보드
- 슈퍼어드민은 별도 role(`PLATFORM_ADMIN`) + 별도 인증 경계

---

## 데이터 모델 (신규 + 변경)

| 테이블 | 유형 | 주요 컬럼 |
|---|---|---|
| `tenants` | 신규 | name, slug(unique), plan, status, audit 6 |
| `tenant_memberships` | 신규 (매핑 엔티티) | tenant_id, user_id, role, granted_by, active, audit 6 |
| `tenant_invitations` | 신규 | tenant_id, email, role, token, expires_at, accepted_at |
| `subscriptions` | 신규 | tenant_id, plan, status, current_period_end (B2 공유) |
| `slots` / `events` / `products` / `bookings` / `payments` / `mcp_tokens` / `operator_inbox_notifications` | **컬럼 추가** | `tenant_id BIGINT` + `INDEX idx_<t>_tenant_id` + 복합 인덱스 `(tenant_id, deleted_at)` |

> 기존 테이블 변경은 **하위 호환 마이그레이션**: nullable 추가 → 백필 → NOT NULL 전환 3단계 (DBA 검토 필수, 락 유발 변경 확인).

---

## 영향 서비스

| 서비스 | 레포 | 변경 |
|---|---|---|
| backend | `/backend` | 신규 `domain/tenant/`, 전 운영 Repository 격리 필터, JWT claim 확장, 마이그레이션 다수 |
| B2B 웹 포털 | `/web` | 테넌트 스위처, 온보딩·초대·멤버관리·슈퍼어드민 콘솔, 모든 portal API에 tenant 컨텍스트 |
| B2C 모바일/웹 | `/mobile`, `/web` | **영향 없음** (USER role 무관) |

---

## 오픈 이슈 / 결정 필요

| # | 이슈 | 영향 FR | 결정권자 |
|---|---|---|---|
| 1 | 격리 방식 — Hibernate @Filter vs QueryDSL baseline predicate | FR-02 | 기술 리드 |
| 2 | 기존 `ownerId` 데이터 백필 정책 — 개인 테넌트 자동 생성? | FR-02 | 기술 리드 |
| 3 | `tenant_id` NOT NULL 전환 시점 — 백필 완료 검증 기준 | FR-02 | DBA |
| 4 | 플랜 tier 경계 — 어떤 기능을 어느 플랜에 | FR-05 | 사업/PO |
| 5 | 슈퍼어드민 인증 분리 수준 — 별도 도메인? IP 제한? | FR-07 | 보안 |

---

## 출시 범위 (제안)

- **v0.1 (기반)**: FR-01 + FR-03 + FR-04 — 테넌트·멤버십·컨텍스트·온보딩. **데이터 격리 전, 신규 조직만**.
- **v1.0 (격리)**: FR-02 + FR-06 — 기존 데이터 백필 + 전 Repository 격리. **가장 위험한 단계, 격리 회귀 스위트 필수**.
- **v1.1 (과금)**: FR-05 + FR-07 — 구독·슈퍼어드민.

> FR-02(격리)가 전체의 리스크 80%입니다. cross-tenant 누수는 보안 사고이므로 **격리 회귀 테스트를 모든 Repository에 강제**하고, 단계적 롤아웃(섀도 → 일부 → 전체)을 권장합니다.

---

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-31 | 초안 작성 (C1 아이디어 → PRD) | biuea3866 |
