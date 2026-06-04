# B2C/B2B 권한 체계 고도화 (Authorization & Access Control) PRD

> 작성일: 2026-05-31 | 작성자: biuea3866@gmail.com
> 소스: 권한 체계 고도화 요구 — B2C 멤버 등급/멤버십 기능 게이팅 + B2B 기업 계층형 권한
> 관련 PRD: [구독 & 멤버십](../20260531_subscription-membership/prd.md) (등급의 '출처'), [B2B 인사이트 대시보드](../20260523_b2b-insights-dashboard/prd.md) (권한 체계와 독립)
> 벤치마킹 자료: [research-permission-patterns.md](./research-permission-patterns.md)
> prd-reviewer 검수: 2026-05-31 NEEDS_REVISION → Must Fix M1~M4 반영 (하단 검수 이력)

---

## 진행 현황 (Status Ledger)

> 최종 갱신: 2026-05-31 | 전체: 0/9 배포완료 (0%)

| FR | 기능 | 상태 | 구현(PR) | 배포 | 비고 |
|----|------|------|---------|------|------|
| FR-01 | 사용자 계정 타입(B2C/B2B) 구분 | ⬜ 대기 | — | — | User에 accountType 도입 |
| FR-02 | B2C 멤버 등급 도메인 (MemberGrade) | ⬜ 대기 | — | — | 멤버십 PRD 연계 |
| FR-03 | Entitlement(혜택/기능) 1급 객체 + 등급 매핑 | ⬜ 대기 | — | — | Stripe Entitlements 패턴 |
| FR-04 | 등급별 기능 게이팅 강제 (서버 권위) | ⬜ 대기 | — | — | FR-03 선행 |
| FR-05 | Organization(기업) 도메인 신설 | ⬜ 대기 | — | — | B2B 루트 |
| FR-06 | OrganizationMember + org-scoped 역할(OWNER/STAFF) | ⬜ 대기 | — | — | FR-05 선행 |
| FR-07 | 기존 전역 FACILITY_OWNER → org-scoped 마이그레이션 | ⬜ 대기 | — | — | 하위호환 |
| FR-08 | 멤버 초대 플로우 (invite/accept/revoke) | ⬜ 대기 | — | — | FR-06 선행, 알림 재사용 |
| FR-09 | org-scoped 권한 검사 통합 (가드 확장) | ⬜ 대기 | — | — | FR-06·07 선행 |

### 상태 정의 (증거 기반 — `rules/COMPLETION-RULE.md` 연계)

| 상태 | 진입 조건 (증거) |
|---|---|
| ⬜ 대기 | 티켓만 존재 |
| 🟡 진행중 | 브랜치 존재 / 커밋 시작 |
| 🔵 구현완료 | dev 머지 PR + COMPLETION-RULE §1~4 충족 (테스트 통과 아티팩트) |
| ✅ 배포완료 | release 태그 / 배포 로그 첨부 |

---

## 배경 (Background)

권한 체계가 **플랫(flat)** 합니다. 등급 개념이 없고, 조직(기업) 개념이 없습니다. AS-IS는 코드 대조로 확인했습니다.

| 자산 | 실제 코드 상태 (검증) | 한계 |
|---|---|---|
| `User` (`domain/user/User.kt`) | email/passwordHash/status + 역할 검증 헬퍼만 | **멤버 등급(grade/tier) 필드 없음, 조직 참조 없음, B2C/B2B 구분 없음** |
| RBAC 테이블 | `roles`/`permissions`/`user_roles`/`role_permissions` 4개(MySQL), 모두 매핑 Entity (`@ManyToMany` 미사용, 자체 PK·audit·unique) | 구조는 건전. 단 **역할이 전역(global)** |
| 역할 (시드 = 5종) | USER / ADMIN / FACILITY_OWNER (`V2:81-84`) + EVENT_HOST / GOODS_SELLER (`V19:25,29`) | **전부 플랫 전역 역할 — 조직 스코프 없음.** USER는 권한 emptySet |
| 역할 (유령 = 1종) | `OPERATIONS_MANAGER` — `OperationKpiApiController.kt:37` `@PreAuthorize`에만 등장, **roles 테이블 시드 0건** | **기존 결함** — 누구에게도 부여 불가능한데 게이팅에 사용. FR-07/09 인벤토리에서 정리 대상 |
| 권한 enforcement | `@PreAuthorize` 26곳 + `OwnershipGuard.requireOwned`(코드 기반 "내 리소스" 검사) 혼재. 26곳 중 다수는 MCP 도구의 `@authz.hasMcpScope(...)`(Role 아닌 **MCP 토큰 scope**) | 등급 게이팅 수단 0건. 조직 단위 권한 위임 불가 |
| `OwnershipGuard.requireOwned` 특이동작 | `ownerUserId == null`이면 **통과**(`OwnershipGuard.kt:17-18` 주석 — admin 시드/B2C 호환 리소스) | null 소유 리소스는 현재 사실상 누구나 접근 — 이관(FR-07) 시 충돌 주의 |
| 리소스 소유 — 저장소 이종 | **Facility = MongoDB Document**(`Facility.kt:14` `@Document` + `BaseDocument`), `ownerUserId: Long?`(`Facility.kt:47`) 1인 단일 소유. **Event/Product = MySQL JPA**(`Event.kt:15`·`Product.kt:14` `@Entity`), ownerId(Long) | 소유자=개인. organizationId 부여 시 **Facility는 Flyway 아님(Mongo 컬렉션 필드+백필 스크립트)**, Event/Product만 Flyway(발번 큐) |
| 멤버 등급 게이팅 | 없음 | 구독/멤버십(별도 APPROVED PRD)이 등급을 만들어도 **그 등급이 무엇을 잠금해제하는지 강제하는 계층이 없음** |
| 핸드오프 노트 (2026-05-21) | "권한 세분화 ← 여기부터", "멤버 등급 미구현 — User에 grade/tier 없음, 마이그레이션 필요", "Role 전역 — 조직별 스코프 미구현", "facility-owner ownership은 OwnershipGuard로 임시 처리 중" | 본 PRD가 이어받는 지점 명시 |

→ B2C는 등급별 차등 경험을 강제할 수 없고, B2B는 "기업+직원" 구조 자체가 없어 마스터/일반 권한 분리를 표현할 수 없습니다. 본 PRD는 **기존 RBAC를 재사용·확장**해 (1) B2C 등급→entitlement→기능 게이팅, (2) B2B 조직 계층형 RBAC 두 축을 추가합니다.

### 멤버십 PRD와의 경계 (중복 금지)

| 책임 | 멤버십 PRD ([구독 & 멤버십](../20260531_subscription-membership/prd.md)) | 본 PRD (권한 체계) |
|---|---|---|
| MemberGrade **enum 정의** | — (멤버십 PRD엔 등급 enum 없음. `SubscriptionPlan(name)`+`benefits_json`만) | **본 PRD가 소유** — grade 도메인·enum(BASIC/SILVER/GOLD/VIP) 정의 (FR-02) |
| 등급의 **부여/회수** | 구독 결제·빌링키·라이프사이클로 **plan→grade 부여/회수** | 부여된 등급을 입력으로 받음 |
| 등급이 **무엇을 잠금해제** | (미설계) | Entitlement 모델로 정의 (FR-03) |
| 등급별 **강제** | (미설계) | 서버 권위 게이팅 (FR-04) |
| B2B 조직 | **비목표로 명시 제외** | 본 PRD가 담당 (FR-05~09) |

멤버십 PRD는 "결제하면 GOLD가 된다"까지, 본 PRD는 "GOLD가 무엇을 할 수 있고 어떻게 막는가"를 담당합니다.

---

## 벤치마킹 (Benchmarking)

전문은 [research-permission-patterns.md](./research-permission-patterns.md). 핵심 비교:

### B2C 등급/멤버십 기능 게이팅

| 제품 | 접근 방식 | 게이팅/강제 방식 | 우리가 참고할 점 |
|------|----------|-----------------|-----------------|
| Stripe Entitlements | Product에 Feature 연결 → 구독 시 active entitlement 부여 | feature lookup_key 기반 조회·게이팅 | **Entitlement를 1급 객체로** — plan↔feature 분리, 활성 entitlement 조회/캐시 (채택) |
| 쿠팡 와우 | 단일 유료 멤버십 | 구독 boolean으로 혜택 전체 ON/OFF | 단일 등급도 강력 — boolean entitlement로 시작 (변형 차용) |
| 무신사 등급 | 누적 구매액 5단계(로열티) | 자동 산정, 결제 구독 아님 | **등급(로열티)과 멤버십(구독)은 별개 축** — 합치지 않음 (채택) |
| Spotify | 플랜별 feature flag(오프라인·광고제거) | 서버가 entitlement 발급, 클라 신뢰 안 함 | 서버 권위 pull, 클라이언트 신뢰 금지 (채택) |
| 토스프라임 | 구독 + 혜택별 월 N회 한도 | quota 기반 | 혜택 3종(boolean/수치형/quota) `{type,value}` 통일 (채택) |

### B2B 조직 계층형 RBAC

| 제품 | 접근 방식 | 게이팅/강제 방식 | 우리가 참고할 점 |
|------|----------|-----------------|-----------------|
| GitHub Org | Org Owner/Member + Team + repo 권한 | **역할 스코프(org)와 권한 스코프(resource) 분리** | 전역 Role을 org-scoped로 전환의 핵심 근거 (채택) |
| Slack | Primary Owner>Owner>Admin>Member>Guest | 역할별 관리 기능 차등 | Owner/Member 2역할 + 향후 Guest (변형 — 2역할로 시작) |
| Notion | Workspace→Teamspace→Page 3단 | 3단 스코프 | 우리 규모엔 과함 → **2단(org→resource)로 축소** (미채택/축소) |
| Jira/Atlassian | org→site→product→project 4단 | 다단계 | 과함 (미채택) |
| Stripe team | 역할 프리셋(Admin/Developer/Analyst…) | 프리셋 기반 | 마스터=OWNER, 일반=STAFF 프리셋 (채택) |
| Google Zanzibar/OpenFGA | 관계 그래프(ReBAC) | 위임·계층에 강력 | 지금 미도입, 위임 깊어지면 경계만 남김 (미채택/경계) |

> RBAC vs ABAC vs ReBAC 트레이드오프 및 우리 적용 권장 패턴(차용/변형/미채택 판단)은 research-permission-patterns.md 참조.

---

## 목표 (Goals)

- B2C 멤버 등급별 기능 차등을 **서버에서 강제** — 클라이언트 우회 차단(게이팅 우회 0건)
- 등급↔기능 매핑을 **데이터(Entitlement)로 관리** — 코드 배포 없이 혜택 조정 가능
- B2B **기업(Organization) + 직원(Member) 구조** 도입 — 한 기업에 마스터 1+·일반 N
- 마스터/일반 등급별 **접근 페이지·기능 차등**을 org-scoped로 강제
- 기존 전역 FACILITY_OWNER/EVENT_HOST/GOODS_SELLER 사용자를 **무중단 하위호환** 이관(권한 회귀 0건)
- 멤버 추가를 **초대 기반**으로 — 무단 가입 0건

## 비목표 (Non-Goals)

- 등급의 **부여/결제 로직** — 멤버십 PRD 소유 (본 PRD는 등급을 입력으로 받음)
- 커스텀 역할 빌더 — OWNER/STAFF 2역할 프리셋으로 시작
- ReBAC(OpenFGA/Zanzibar) 도입 — 2단 스코프로 충분, 경계만 남김
- 3단 이상 스코프(Notion teamspace·Jira project) — org→resource 2단만
- ABAC 동적 정책 엔진 — 정적 RBAC + ownership 보조
- 기존 RBAC 테이블 전면 재작성 — 재사용·확장

---

## 사용자 스토리 (User Stories)

| As a | I want to | So that |
|------|-----------|---------|
| B2C 일반 사용자 | 내 등급으로 가능한 기능이 명확히 보임 | 무엇이 잠겨있고 왜인지 안다 |
| B2C GOLD 멤버 | 등급 전용 기능(선예매·할인) 사용 | 멤버십 가치를 체감 |
| 플랫폼 운영자 | 등급별 혜택을 배포 없이 조정 | 프로모션을 빠르게 적용 |
| B2B 기업 마스터 | 우리 기업 계정을 만들고 직원을 초대 | 직원이 같은 시설/경기를 공동 운영 |
| B2B 기업 마스터 | 직원을 마스터/일반으로 구분, 일반은 정산·삭제 차단 | 권한 오남용 방지 |
| B2B 일반 직원 | 허용된 페이지·기능만 접근 | 실수로 민감 작업을 못 함 |
| 기존 FACILITY_OWNER | 이관 후에도 내 시설을 그대로 관리 | 권한 회귀 없음 |

---

## 기능 요구사항 (Functional Requirements)

### FR-01. 사용자 계정 타입(B2C/B2B) 구분
- **결과**: `User`에 `accountType`(B2C / B2B) 도입. 한 계정이 B2C 소비자이면서 B2B 조직 멤버일 수 있는지 정책 확정(오픈이슈 #1). 기본값 B2C, 기존 사용자 전원 B2C로 백필. 회원가입 경로에서 타입 결정.

### FR-02. B2C 멤버 등급 도메인 (MemberGrade)
- **결과**: 멤버 등급 도메인·enum(예: BASIC/SILVER/GOLD/VIP)을 **본 PRD가 정의·소유**. **등급 부여/회수의 트리거는 멤버십 PRD(구독 plan→grade)** 이며, 본 PRD는 부여된 등급을 **읽어서 게이팅에 사용**. "현재 사용자의 유효 등급" 조회 경로 정의. 멤버십 PRD의 `SubscriptionPlan`↔MemberGrade 매핑 책임은 멤버십 측, enum 정의는 본 PRD 측으로 귀속(오픈이슈 #2). 향후 로열티(누적구매) 등급 축과 구독 등급 축을 분리 가능하게 설계.

### FR-03. Entitlement(혜택/기능) 1급 객체 + 등급 매핑
- **결과**: `Entitlement`를 1급 객체로(Stripe 패턴). `{featureKey, type, value}` 구조 — type 3종: BOOLEAN(기능 잠금해제) / NUMERIC(적립률 등 수치) / QUOTA(월 N회 한도). 등급↔entitlement 매핑 테이블(`grade_entitlements`)로 관리해 **코드 배포 없이 조정**. featureKey 네이밍 컨벤션 정의(예: `ticketing:presale`, `goods:member_discount`). RBAC permission(역할 기반)과 entitlement(등급 기반)의 **경계·우선순위** 명시(오픈이슈 #3).

### FR-04. 등급별 기능 게이팅 강제 (서버 권위)
- **결과**: 거래/기능 진입 시 서버가 사용자 유효 등급의 entitlement를 조회해 **허용/차단/한도 검사**. 클라이언트는 표시용으로만 entitlement를 받고 **적용 시점에 서버 재검증**(클라 신뢰 금지). QUOTA 타입은 사용량 카운트·잔여 검사(원자적). 차단 시 표준 에러(403 또는 도메인 예외 → 4xx)와 "필요 등급" 안내. 게이팅 검사 위치는 기존 가드 컨벤션(코드 기반)과 일관(오픈이슈 #4 — 가드 vs 어노테이션).

### FR-05. Organization(기업) 도메인 신설
- **결과**: `Organization`(기업) Entity 신설(MySQL/JPA) — 사업자명/식별정보/상태. B2B의 루트 aggregate. 리소스에 `organizationId` 부여:
  - **Event/Product (MySQL/JPA)**: Flyway(발번 번호)로 `organization_id` 컬럼 추가.
  - **Facility (MongoDB Document)**: Flyway 아님 — 컬렉션 필드 추가 + 별도 백필 스크립트. no-db-fk 제약은 **MySQL 리소스에만** 적용.
- 기존 `ownerUserId`(개인 소유)와 `organizationId`(조직 소유)의 공존·전환 전략(FR-07 연계). Organization 루트 soft-delete 시 OrganizationMember·Invitation 자식 **동반 soft-delete**(aggregate 고아 금지).

### FR-06. OrganizationMember + org-scoped 역할(OWNER/STAFF)
- **결과**: `OrganizationMember`(orgId, userId, orgRole) 매핑 Entity 신설(`@ManyToMany` 금지 → 독립 Entity). orgRole 프리셋 2종: **OWNER(마스터)** = 멤버 초대/역할 변경/정산/조직·리소스 삭제, **STAFF(일반)** = 운영(슬롯·재고·경기 관리)만. 페이지·기능별 OWNER/STAFF 허용 매트릭스 정의. 한 조직에 OWNER 최소 1인 보장(마지막 OWNER 강등/탈퇴 차단).

### FR-07. 기존 전역 FACILITY_OWNER → org-scoped 마이그레이션
- **결과**: 기존 전역 역할(FACILITY_OWNER/EVENT_HOST/GOODS_SELLER + **미시드 유령 역할 OPERATIONS_MANAGER 정리**)과 `ownerUserId`로 소유된 리소스를 **무중단 이관**. 전략: 기존 owner 1인당 1 Organization 자동 생성 → 해당 user를 OWNER로 등록 → 그의 리소스에 organizationId 백필.
- **이종 저장소 주의**: Facility(MongoDB)·Event/Product(MySQL) 백필은 **한 트랜잭션으로 묶을 수 없음** — 저장소별 멱등 백필 + 검증.
- **null-owner 리소스**: `ownerUserId == null`(admin 시드/공유 리소스)은 어느 org에도 안 들어가므로 별도 분류·정책 필요(현 `requireOwned` null 통과 동작과 org-scoped 검사 충돌 방지).
- **다중 역할 user**: 한 user가 여러 운영 역할 보유 시 org 1개 vs 역할별 분리(오픈이슈 #9).
- **권한 회귀 0건 측정**: 이관 전 `(user, 접근 가능 리소스)` 스냅샷 ↔ 이관 후 diff 산출물로 검증(NFR). 롤백 플랜 포함. 전역 역할 즉시 제거 vs 유예 병행(오픈이슈 #5).

### FR-08. 멤버 초대 플로우 (invite/accept/revoke)
- **결과**: OWNER가 이메일로 직원 초대 → 초대 상태(PENDING/ACCEPTED/REVOKED/EXPIRED) → 수락 시 OrganizationMember 생성. **기존 Notification/이메일 Gateway 재사용**(외부 직접 호출 금지). 초대 만료(TTL), 재발송, 회수. 이미 가입된 이메일/미가입 이메일 분기. 멤버 제거 시 org-scoped 역할만 회수(계정 자체는 유지, soft-delete).

### FR-09. org-scoped 권한 검사 통합 (가드 확장)
- **결과**: 기존 `@PreAuthorize`(전역 hasRole) + `OwnershipGuard.requireOwned`(본인 리소스) 혼재 상태에, **org-scoped 검사**("이 사용자가 이 리소스의 조직 멤버이며 필요한 orgRole인가")를 더한 통합 접근 정책. 검사 축 명확화: **전역(ADMIN) / 조직(OWNER·STAFF) / 본인(ownership) / MCP scope(`@authz.hasMcpScope`)** — MCP 토큰 scope를 org-scoped 통합 대상에 포함할지/제외할지 결정(오픈이슈 #4, N1).
- **`requireOwned` null 통과 처리**: 현재 `ownerUserId == null` 통과(`OwnershipGuard.kt:17-18`) 동작을 org-scoped 전환 시 어떻게 다룰지 명시(이관 후 organizationId로 대체되는 리소스 vs 영구 공유 리소스 구분).
- 기존 26개 `@PreAuthorize` 사용처(Mcp*Tools 12개 포함)와 OwnershipGuard 호출부의 영향 인벤토리·전환 계획.

---

## 비기능 요구사항 (Non-Functional Requirements)

- **성능**: entitlement·org-membership 조회는 모든 거래 경로에 추가됨 → 캐시 전략(등급/멤버십 변경 시 무효화). 권한 검사가 핵심 API p95에 주는 영향 측정·상한.
- **보안**: 게이팅·org 권한은 **서버 권위**. 클라이언트 전달 등급/역할 신뢰 금지. org 리소스 IDOR 차단(타 조직 리소스 접근 0건). 권한 변경은 audit(JpaAuditingBase) 기록.
- **정합성**: 마지막 OWNER 보장(조직 무소유 0건), QUOTA 원자적 차감(동시 요청 음수 0건 — 잔량 차감형이라 DB unique로 못 막음 → `@Version` 낙관락 또는 조건부 UPDATE), 마이그레이션 권한 회귀 0건(이관 전후 접근 리소스 diff 스냅샷으로 검증).
- **운영**: 게이팅 차단율·등급별 사용량 메트릭, 초대 수락율, org별 멤버 수. 마이그레이션 전후 권한 diff 리포트.
- **하위호환**: 이관 중 기존 운영자 API 무중단. 전역 역할 제거 시점 단계적.

---

## 제약 조건 (Constraints)

- Hexagonal + Rich Domain, JPA Entity=Domain Entity 단일 모델, audit 6컬럼 + soft delete.
- **`@ManyToMany` 금지** — OrganizationMember·grade_entitlements는 독립 매핑 Entity.
- **no-db-fk** — 조직/리소스 참조는 FK id(Long)만 보유. unique 제약은 허용(중복 멤버십 방지).
- QueryDSL(`@Query` 금지), ZonedDateTime, 도메인 패키지 간 import 금지(`domain.common`만 허용).
- 기존 RBAC 테이블/역할/가드 **재사용·확장** — 전면 재작성 금지.
- 외부 호출(이메일·알림)은 트랜잭션 밖, Gateway 경유.
- Flyway 번호: 메인 dev는 V33이나 **in-flight 워크트리에 V34·V35가 이미 점유**(V34 2개 충돌 포함)되어 있고 **feature-flag PRD도 경합**한다. 특정 번호를 고정하지 말고 **머지 시점 단일 발번 주체가 가용 번호(추정 V36~)를 발급**받는다.

---

## 영향 범위 (Scope)

| 레포 | 변경 유형 | 설명 |
|---|---|---|
| backend | 신규 | `domain/organization/`(Organization, OrganizationMember, Invitation), `domain/entitlement/`(Entitlement, grade 매핑), org-scoped 가드, 마이그레이션(발번 큐) |
| backend | 수정 | `User`(accountType, 등급 참조), **Event/Product(organization_id — Flyway)**, **Facility(organizationId — MongoDB 필드, Flyway 아님)**, 기존 `@PreAuthorize`·`OwnershipGuard.requireOwned` 호출부, AdminUser/Role 관리 API, OPERATIONS_MANAGER 유령 역할 정리 |
| mobile / web(B2C) | 신규/수정 | 등급별 기능 표시·잠금 UI, 차단 안내 |
| web(B2B 포털) | 신규/수정 | 기업 멤버 관리·초대 화면, OWNER/STAFF별 메뉴·페이지 게이팅 |
| Kafka | 검토 | `member.grade.changed.v1`(멤버십 PRD 연계), `org.member.invited.v1` 필요 여부 검토(오픈이슈 #7) |

데이터 모델 (저장소 구분):
- 신규(MySQL/Flyway, 발번 큐 V36~): `organizations`/`organization_members`/`invitations`/`entitlements`/`grade_entitlements`.
- 변경(MySQL/Flyway): `users`(account_type, grade 관련), `events`/`products`(organization_id).
- 변경(MongoDB, **Flyway 아님**): `facilities` 컬렉션에 `organizationId` 필드 + 백필 스크립트.

### 확인된 누락 티켓 (검수 도출)

| 제목 | 레포 | 이유 |
|---|---|---|
| OPERATIONS_MANAGER 유령 역할 정리 (시드 추가 또는 `@PreAuthorize` 제거) | backend | `OperationKpiApiController.kt:37` 참조 있으나 시드 0건 — FR-07/09 선행 |
| Facility(MongoDB) organizationId 백필 — Flyway 아님, 별도 스크립트 | backend | Facility는 Document라 Flyway 대상 아님 (M1) |
| 이관 전후 (user, 접근가능 리소스) 권한 diff 스냅샷 산출 | backend | FR-07 "회귀 0건" 측정 증거 (N2) |
| Organization 루트 soft-delete → Member/Invitation 자식 전파 | backend | aggregate 고아 금지 (N3) |

---

## 오픈 이슈 (Open Issues)

| # | 질문 | 담당 | 기한 |
|---|------|------|------|
| 1 | 한 계정이 B2C 소비자 + B2B 조직 멤버 동시 가능? (단일 vs 겸용) | PO/기술 리드 | FR-01 전 |
| 2 | 멤버 등급 축 — 구독(멤버십 PRD) 단일 vs 로열티(누적구매) 병행 | PO | FR-02 전 |
| 3 | RBAC permission(역할)과 entitlement(등급)의 경계·우선순위 (둘 다 막으면?) | 기술 리드 | FR-03 전 |
| 4 | 게이팅 강제 위치 — 코드 가드 vs `@PreAuthorize`/커스텀 어노테이션 일관화 방향 | 기술 리드 | FR-04 전 |
| 5 | 전역 FACILITY_OWNER 등 — 이관 후 즉시 제거 vs 유예 병행 기간 | 기술 리드 | FR-07 전 |
| 6 | org-role 프리셋 2종(OWNER/STAFF)으로 충분? 정산 전용·읽기전용 등 추가? | PO | FR-06 전 |
| 7 | 권한·등급 변경 전파 — Kafka 이벤트 vs pull(실시간 조회) | 기술 리드 | FR-04·09 전 |
| 8 | 조직당 멤버 수·조직 수 상한, 요금제 연동 여부 | PO/사업 | FR-05 전 |
| 9 | 기존 EVENT_HOST/GOODS_SELLER도 org-scoped 대상인가, 별도 조직 유형인가 | 기술 리드 | FR-05·07 전 |

---

## 마일스톤

| 단계 | 내용 | 목표일 |
|------|------|--------|
| v1.0 (B2C) | FR-01 + FR-02 + FR-03 + FR-04 — 등급 구분·entitlement·게이팅 강제 | TBD |
| v1.1 (B2B 기반) | FR-05 + FR-06 + FR-08 — 조직·멤버·초대 | TBD |
| v1.2 (B2B 이관) | FR-07 + FR-09 — 전역→org-scoped 마이그레이션·가드 통합 | TBD |

> FR-07(마이그레이션 권한 회귀 0건)·FR-04(게이팅 우회 0건)이 핵심 리스크. FR-09는 기존 26개 @PreAuthorize·OwnershipGuard 호출부 전수 영향이 있어 영향 인벤토리 선행 필수.

---

## 검수 이력 (prd-reviewer)

| 일자 | 판정 | Must Fix | 반영 |
|---|---|---|---|
| 2026-05-31 | NEEDS_REVISION | M1 Facility=MongoDB(Flyway 아님) / M2 OPERATIONS_MANAGER 미시드 유령 역할 / M3 가드 `requireOwned`+null통과 동작 / M4 MemberGrade enum 소유권 모순 | 4건 전부 반영 — AS-IS 저장소 이종 분리, 시드 5종+유령 1종 정정, `requireOwned` null통과 FR-09 반영, grade enum 본 PRD 귀속. N2·N3·N4 누락 티켓·NFR 보강 |

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-31 | 초안 — AS-IS 코드 대조 + 벤치마킹(웹서치) 반영 + /prd 표준 | biuea3866 |
| 2026-05-31 | prd-reviewer Must Fix M1~M4 반영 (Facility MongoDB·유령 역할·가드명·등급 소유권) + N2~N4 보강 | biuea3866 |
