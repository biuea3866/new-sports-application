# B2B SaaS 멀티테넌시 (Multi-Tenancy) PRD

> 작성일: 2026-05-31 | 작성자: biuea3866@gmail.com
> 소스: C1 비즈니스 확장 — 운영자 포털 + MCP를 조직 단위로 격리·구독 판매
> 관련 PRD: [AI 운영 어시스턴트](../20260531_ai-ops-assistant/prd.md), [구독/멤버십](../20260531_subscription-membership/prd.md)
> prd-reviewer 검수: 2026-05-31 NEEDS_REVISION → Must Fix 5건 반영 → **재검수 APPROVED** (하단 검수 이력)

---

## 진행 현황 (Status Ledger)

> 최종 갱신: 2026-05-31 | 전체: 0/9 배포완료 (0%)

| FR | 기능 | 상태 | 구현(PR) | 배포 | 비고 |
|----|------|------|---------|------|------|
| FR-00 | 공통 테넌트 격리 베이스 신설 (선행) | ⬜ 대기 | — | — | M4 — BaseRepositoryImpl 부재, 47개 RepoImpl 전제 |
| FR-01 | Tenant(조직) 도메인 + 멤버십 | ⬜ 대기 | — | — | 전 FR 선행 |
| FR-02 | 데이터 격리 (tenant_id 전파 + 강제 필터) | ⬜ 대기 | — | — | FR-00·01 선행, 최대 리스크 |
| FR-02b | Mongo facilities 격리 (전용 메커니즘) | ⬜ 대기 | — | — | M1 — JPA @Filter 미적용 |
| FR-02c | KPI 집계 키 tenant 재작성 | ⬜ 대기 | — | — | M2 — 집계 단위 변경 |
| FR-03 | 테넌트 컨텍스트 미들웨어 (JWT claim) | ⬜ 대기 | — | — | FR-01 선행 |
| FR-04 | 테넌트 온보딩 + 멤버 초대 | ⬜ 대기 | — | — | FR-01 선행 |
| FR-05 | 플랜/구독 과금 | ⬜ 대기 | — | — | 구독 PRD 엔진 공유 |
| FR-06 | 테넌트별 MCP 토큰·webhook + 캐시 격리 | ⬜ 대기 | — | — | M5 — 전역 캐시 키 |
| FR-07 | 슈퍼어드민 콘솔 | ⬜ 대기 | — | — | FR-01 선행 |

### 상태 정의 (증거 기반 — `rules/COMPLETION-RULE.md` 연계)

| 상태 | 진입 조건 (증거) |
|---|---|
| ⬜ 대기 | 티켓만 존재 |
| 🟡 진행중 | 브랜치 존재 / 커밋 시작 |
| 🔵 구현완료 | dev 머지 PR + COMPLETION-RULE §1~4 충족 (테스트 통과 아티팩트) |
| ✅ 배포완료 | release 태그 / 배포 로그 첨부 |

---

## 배경 (Background)

현재 B2B 운영자 기능은 **개별 사용자(`ownerId` / `userId`) 단위**로 동작하고 조직(Tenant) 개념이 없습니다. AS-IS는 검수에서 코드 대조로 확인됐습니다(아래 일치).

| 도메인 | 현 소유권 (코드 확인) | 한계 |
|---|---|---|
| `Slot` | `Slot.kt:27` `ownerId: Long` | 시설 체인 공동 운영 불가 |
| `Event` | `Event.kt:37` `ownerId: Long` | 구단 공동 관리 불가 |
| `Product` | `Product.kt:39` `ownerId: Long` | 판매팀 권한 분리 불가 |
| `Facility` (Mongo) | `Facility.kt:14` `@Document`, `:47` `ownerUserId: Long?` | **Mongo — JPA 격리와 별도 메커니즘 필요 (M1)** |
| `McpToken` | `McpToken.kt:19` `userId: Long` | 조직 단위 토큰·rate limit 불가 |

→ **B2B를 구독 SaaS로 팔 단위(조직 = 과금·격리 경계)가 없습니다.** 본 PRD는 Tenant를 1급 도메인으로 도입하고 운영 데이터를 테넌트로 격리하며 구독 과금을 붙입니다.

**검수로 드러난 격리 함정** (단순 "Repository baseline 필터"로 안 풀리는 것):
1. `facilities`는 Mongo라 JPA `@Filter`/QueryDSL이 안 먹힘 — 전용 격리 필요 (M1)
2. KPI는 owner당 합산이라 **집계 키 자체**를 tenant로 바꿔야 함 (단순 행 필터 ✗) (M2)
3. **Booking/Payment는 B2C 예약자와 B2B operator가 한 행에 공존** → "B2C 무영향"이 자동 성립 안 함 (M3)
4. 공통 `BaseRepositoryImpl`이 **없음** — 47개 RepoImpl에 격리를 거는 베이스부터 신설 (M4)
5. 인기상품 Redis 캐시가 **전역 키** — 테넌트 넘어 노출 (M5)

---

## 목표 (Goals)

- 과금 가능 단위 **개인 → 조직(테넌트)**
- 데이터 cross-tenant 누수 **0건** (격리 회귀 테스트로 전 Repository 강제)
- 신규 조직 셀프 온보딩 **10분 이내**
- B2C(USER) 거래 플로우 **회귀 0건**

---

## 비목표 (Non-Goals)

- 물리적 DB 분리 — 단일 DB + `tenant_id` 행 격리(shared schema)로 시작
- B2C 소셜(Post/Message — JPA, 일반 사용자 기능) 격리 — 대상 아님 (N1)
- 기존 데이터 대규모 재설계 — 컬럼 추가 + 백필 점진 적용
- 실 PG 빌링 깊이 — 과금 엔진은 구독 PRD 공유

---

## 사용자 스토리 (User Stories)

| As a | I want to | So that |
|---|---|---|
| 시설 체인 대표 | 조직 생성 + 매니저 초대 | 운영 분담 |
| 조직 매니저 | 우리 조직 데이터만 관리 | 타 조직 노출 없음 |
| 조직 대표 | 조직 전체 매출·가동률 한 화면 | 개인별로 안 돌아다님 |
| 보안 담당 | 우리 조직 MCP 토큰·캐시 격리 | 데이터 사고 방지 |
| 플랫폼 슈퍼어드민 | 미납 조직 정지 / 사용량 조회 | 과금·운영 통제 |

---

## 기능 요구사항 (Functional Requirements)

### FR-00. 공통 테넌트 격리 베이스 신설 (선행) — 검수 M4
- **결과**: 공통 `BaseRepositoryImpl`(현재 0건)을 신설하고 QueryDSL baseline predicate에 `tenant_id = :currentTenant` 주입. 47개 RepositoryImpl이 이를 상속/경유하도록 전환. 격리 누락이 곧 보안 사고이므로, **모든 Repository에 "테넌트 A로 B 데이터 0행" 격리 회귀 스위트**를 강제. 손으로 47개에 거는 방식 금지.

### FR-01. Tenant(조직) 도메인 + 멤버십
- **결과**: `Tenant`(name, slug, plan, status ACTIVE/SUSPENDED). `TenantMembership` 매핑 엔티티(**@ManyToMany 금지**: tenant_id, user_id, role OWNER/MANAGER/VIEWER, granted_by, active). 한 user 복수 테넌트 소속. 역할별 권한.

### FR-02. 데이터 격리 (tenant_id 전파 + 강제 필터) — 최대 리스크
- **결과**: 운영 엔티티에 `tenant_id` 추가, FR-00 베이스로 강제 필터. `ownerId`는 "생성자" 유지, `tenant_id`가 "소유 조직". cross-tenant 접근 404(은닉). **단 Booking/Payment는 FR-02 일반 격리에서 제외** — M3 참조(별도 NULL 정책).

### FR-02b. Mongo facilities 격리 (전용 메커니즘) — 검수 M1
- **결과**: `facilities`(Mongo `@Document`)에 `tenant_id` 추가. JPA `@Filter`/QueryDSL 미적용이므로 **Mongo 전용 격리**(Repository 메서드에 tenant 조건 강제 또는 Spring Data Mongo `@Document` 레벨 필터). Slot.facilityId(String) ↔ Facility 간 tenant 일관성 주체 명시.

### FR-02c. KPI 집계 키 tenant 재작성 — 검수 M2
- **결과**: `GetOperationKpiUseCase` + `aggregateFacilityKpi`/`aggregateGoodsKpi`/`aggregateTicketKpi`(booking/goods/ticketing DomainService)의 집계 인자를 `ownerUserId` → `tenantId`로 변경. "우리 조직 전체 매출" = 테넌트 내 N명 owner 합산. baseline 행 필터로는 불가 → 집계 쿼리 재작성.

### FR-03. 테넌트 컨텍스트 미들웨어 (JWT claim)
- **결과**: 로그인 시 소속 테넌트 목록 → 활성 테넌트 선택 → JWT `tenant_id` claim. 요청마다 `TenantContext`(Request scope) 확정. 미소속 운영 API 403. 전환 API.

### FR-04. 테넌트 온보딩 + 멤버 초대
- **결과**: 셀프 조직 생성 → 생성자 OWNER + 개인 데이터 마이그레이션 옵션. 이메일 초대(만료 토큰) → 수락 시 멤버십. 역할 변경·제거(OWNER만).

### FR-05. 플랜/구독 과금
- **결과**: Free/Pro/Enterprise 기능 게이트 + 시트 enforcement. 월 구독은 **구독 PRD 엔진 공유**. 미납 SUSPENDED → 조회만.

### FR-06. 테넌트별 MCP 토큰·webhook + 캐시 격리 — 검수 M5
- **결과**: MCP 토큰 `tenant_id` 귀속, rate limit·audit·인사이트 테넌트 집계. webhook 격리. **캐시 키에 tenant 접두사** — `PopularProductsRedisRepository`(현재 `popular:products:{category}` 전역) + `CacheConfig` `@Cacheable` 키 전략 점검. 테넌트 정지 시 토큰 일괄 비활성.

### FR-07. 슈퍼어드민 콘솔
- **결과**: 전체 테넌트 목록·상태·플랜·사용량. 강제 정지/해제, 플랜 조정. 과금·MAU·MCP 호출량 대시보드. 별도 role(`PLATFORM_ADMIN`) + 별도 인증 경계.

---

## 비기능 요구사항 (Non-Functional Requirements)

- **성능**: 격리 필터가 모든 쿼리에 추가 → `(tenant_id, deleted_at)` 복합 인덱스 필수. EXPLAIN으로 풀스캔 0건 검증.
- **보안**: cross-tenant 누수 = 보안 사고 → 전 Repository 격리 회귀 테스트 강제(FR-00). 슈퍼어드민 권한 분리·접근 audit. JWT tenant claim 위변조 검증.
- **데이터 정합성**: 백필 멱등(재실행 중복 테넌트 0). 이종 저장소(MySQL slot ↔ Mongo facility) 간 tenant 일관성 보장 주체 명시(N2).
- **운영**: 테넌트별 사용량·과금 메트릭. NOT NULL 전환은 백필 완료 검증 후. 단계적 롤아웃(섀도→일부→전체).

---

## 제약 조건 (Constraints)

- 기존 테이블 변경은 **하위 호환 3단계**: nullable → 백필 → NOT NULL(DBA 검토, 락 시간 산정). **Booking/Payment는 B2C 공유라 NOT NULL 전환 대상에서 제외, nullable 유지** (M3).
- B2C(USER) 플로우 회귀 0 — Post/Message는 격리 대상 아님.
- Hexagonal + Rich Domain, audit 6컬럼·soft delete, `@ManyToMany` 금지.

---

## 영향 범위 (Scope)

| 레포 | 변경 유형 | 설명 |
|---|---|---|
| backend | 신규 | `domain/tenant/`, 공통 격리 베이스(FR-00), JWT claim 확장, 마이그레이션 다수 |
| backend | 수정 | 47개 RepositoryImpl 격리 경유, Mongo facility 격리, KPI 집계 3종 키 재작성, 캐시 키 |
| web | 신규/수정 | 테넌트 스위처, 온보딩·초대·멤버관리·슈퍼어드민 콘솔, portal API 테넌트 컨텍스트 |
| mobile / web(B2C) | 영향 최소 | USER 무관 — 단 Booking/Payment tenant_id nullable 정책 확인 |

데이터 모델: 신규 `tenants`/`tenant_memberships`/`tenant_invitations`/`subscriptions`. 변경 — `slots`/`events`/`products`/`mcp_tokens`/`operator_inbox_notifications`에 `tenant_id`(NOT NULL 가능), `facilities`(Mongo)에 `tenant_id`, **`bookings`/`payments`에 `tenant_id NULL 허용`**.

### 확인된 누락 선행 티켓 (검수 도출)

| 제목 | 이유 |
|---|---|
| 공통 격리 베이스(BaseRepositoryImpl) + QueryDSL baseline 주입 | M4 — 전제 인프라 부재 |
| Mongo facilities tenant 격리 (전용 메커니즘) | M1 — JPA @Filter 미적용 |
| KPI 집계 3종 tenant 키 재작성 | M2 — baseline 필터로 집계 단위 변경 불가 |
| Booking/Payment tenant_id NULL 정책 + B2C 경로 보호 | M3 |
| 캐시 키 tenant 전파 (PopularProducts·CacheConfig) | M5 |

---

## 오픈 이슈 (Open Issues)

| # | 질문 | 담당 | 기한 |
|---|------|------|------|
| 1 | 격리 방식 — Hibernate @Filter vs QueryDSL baseline | 기술 리드 | FR-00 전 |
| 2 | 기존 `ownerId` 백필 — 개인 테넌트 자동 생성? | 기술 리드 | FR-02 전 |
| 3 | `tenant_id` NOT NULL 전환 시점·테이블별 락 시간 | DBA | FR-02 중 |
| 4 | 플랜 tier 경계 | 사업/PO | FR-05 전 |
| 5 | 슈퍼어드민 인증 분리 수준 | 보안 | FR-07 전 |
| 6 | Mongo facility 격리 메커니즘 (JPA @Filter 불가) | 기술 리드 | FR-02b 전 |
| 7 | Booking/Payment tenant_id NULL 정책 확정 | 기술 리드 | FR-02 전 |
| 8 | 이종 저장소(MySQL/Mongo) tenant 일관성 주체 | 기술 리드 | FR-02b 전 |
| 9 | 47개 Repo 일괄 격리 누락 방지 (베이스 강제 vs 회귀 스위트) | 기술 리드 | FR-00 전 |

---

## 마일스톤

| 단계 | 내용 | 목표일 |
|------|------|--------|
| v0.0 (선행) | FR-00 — 공통 격리 베이스 + 회귀 스위트 | TBD |
| v0.1 (기반) | FR-01 + FR-03 + FR-04 — 테넌트·컨텍스트·온보딩 (신규 조직만) | TBD |
| v1.0 (격리) | FR-02 + FR-02b + FR-02c + FR-06 — 백필 + 전 Repository·Mongo·KPI·캐시 격리 | TBD |
| v1.1 (과금) | FR-05 + FR-07 | TBD |

> FR-00·FR-02·02b·02c가 리스크의 대부분. cross-tenant 누수는 보안 사고이므로 격리 회귀 테스트 강제 + 단계적 롤아웃.

---

## 검수 이력 (prd-reviewer)

| 일자 | 판정 | Must Fix | 반영 |
|---|---|---|---|
| 2026-05-31 | NEEDS_REVISION | M1 Mongo facility 격리 누락 / M2 KPI 집계 키 / M3 Booking·Payment B2C 공유 반례 / M4 BaseRepositoryImpl 부재 / M5 캐시 전역 키 | 5건 전부 반영 — FR-00·02b·02c 신설, Booking/Payment NULL 정책, 캐시 격리(FR-06), NFR·제약·누락티켓·오픈이슈 보강 |
| 2026-05-31 | **APPROVED** (재검수) | M1~M5 전부 해소 확인 (코드 기준) | 구현 착수 가능 |

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-31 | 초안 → /prd 표준 → prd-reviewer Must Fix 5건 반영 | biuea3866 |
