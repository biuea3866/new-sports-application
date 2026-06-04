# 권한 체계 웹 리서치 — B2C 등급/멤버십 + B2B 조직 RBAC

스포츠 시설 예약/티켓/굿즈 커머스 플랫폼 권한 체계 고도화 PRD 작성을 위한 타 제품 사례 조사입니다.

---

## 축 1: B2C 멤버 등급/멤버십 기반 기능 게이팅

### 사례 비교표

| 제품 | 등급/멤버십 구조 | 게이팅 방식 | 우리가 참고할 점 |
|---|---|---|---|
| 쿠팡 와우(Wow) | 단일 유료 멤버십(월 구독). 무료배송·로켓프레시·OTT·골드박스 | 구독 활성 여부(boolean)로 혜택 전체 ON/OFF. 등급 세분화 없음 | 단일 멤버십도 강력. 진입은 단순 boolean entitlement로 시작 가능 |
| 네이버 멤버십(플러스) | 월 구독. 적립률 상향 + 디지털 콘텐츠 번들 | 구독 상태 + 적립 booster. 제휴 콘텐츠는 "이용권" 단위 entitlement | 적립률 같은 "수치형 혜택"과 콘텐츠 "이용권"을 분리 모델링 |
| 무신사 등급 | 구매액/활동 누적 5단계(뉴비~다이아). 등급별 쿠폰·적립 | 누적 구매액 기반 자동 산정(로열티). 결제 구독 아님 | 활동 기반 등급(로열티)과 구독 기반 멤버십은 **다른 축** — 분리 |
| 토스프라임 | 월 구독. 토스페이 결제 혜택·수수료 면제 | 구독 boolean + 혜택별 한도(월 N회) | "혜택별 사용 한도(quota)"를 entitlement에 포함 |
| Spotify Premium | Free/Premium(개인·듀오·패밀리·학생) 플랜 | 플랜별 feature flag(오프라인·광고제거·음질). 서버가 entitlement 발급 | 플랜→feature flag 매핑 테이블. 클라이언트는 서버 entitlement만 신뢰 |
| Stripe Entitlements | Product에 Feature 연결 → 구독 시 customer에 active entitlement 부여 | active entitlements 조회 API. feature lookup_key 기반 게이팅 | **entitlement를 1급 객체로** — plan과 feature를 분리하고 활성 entitlement를 조회/캐시 |

### 핵심 발견 (B2C)

1. **등급(grade)과 멤버십(subscription)은 별개 축이다.** 무신사식 "누적 구매액 로열티 등급"과 쿠팡식 "유료 구독 멤버십"은 다르게 모델링. 둘을 한 enum으로 합치면 안 됨.
2. **Entitlement를 1급 도메인 객체로 둔다(Stripe 패턴).** grade/plan이 직접 기능을 가리지 않고, grade→entitlement set→feature 게이팅. 등급 변경 시 entitlement만 재계산.
3. **혜택 유형 3종**: (a) boolean 기능 잠금해제, (b) 수치형 혜택(적립률 5%), (c) 사용 한도/quota(월 무료배송 N회). `{type, value}` 구조로 통일.
4. **게이팅은 서버 권위(pull) — 클라이언트 신뢰 금지.** 클라이언트는 서버 발급 entitlement만 표시. 적용 시점에 서버 재검증.
5. **등급 산정 트리거**: 구독은 결제 webhook, 로열티는 주문 확정 이벤트로 누적액 갱신 → 등급 재평가 배치.

---

## 축 2: B2B 조직 계층형 RBAC

### 사례 비교표

| 제품 | 역할 구조 | 게이팅/스코프 방식 | 우리가 참고할 점 |
|---|---|---|---|
| Slack | Workspace: Primary Owner > Owner > Admin > Member > Guest | 역할별 관리 기능 차등. Guest는 채널 스코프 제한 | Owner/Admin/Member 3층 + Guest(제한 스코프). Primary Owner 단일 |
| Notion | Workspace Owner/Member + Teamspace Owner/Member + 페이지 권한(Full/Edit/Comment/View) | 3계층: 워크스페이스→팀스페이스→페이지 | 조직-하위그룹-리소스 3단 스코프. 페이지 단위 권한 위임 |
| GitHub Org | Org Owner/Member + Team(maintainer/member) + repo 권한(read/triage/write/maintain/admin) | 역할은 org 스코프, 권한은 repo 스코프. Team으로 권한 묶음 부여 | **역할의 스코프(org)와 권한의 스코프(resource) 분리**. Team = 역할 그룹 |
| Linear | Workspace Admin/Member + Guest. 팀(team) 단위 멤버십 | 역할은 workspace 전역, 팀 접근은 멤버십으로 | 단순 2역할(Admin/Member)+Guest로 시작 |
| Stripe | Account team member + 역할(Administrator/Developer/Analyst/View only 등) | 역할별 대시보드 기능·API 키 접근 차등 | 미리 정의된 역할 세트(프리셋) 제공 |
| Atlassian/Jira | Org admin + Site admin + Product role + Project role | 다단계 스코프(org/site/product/project) | 대규모는 다단계, 우리 규모엔 과함 — 2단(org/resource)로 |
| AWS IAM | User/Group/Role + Policy(JSON) | 정책 기반(ABAC 가능). 매우 세분 | 세분 정책은 강력하나 복잡 — RBAC 프리셋 우선 |

### RBAC vs ABAC vs ReBAC 트레이드오프

| 모델 | 설명 | 장점 | 단점 | 적합도 |
|---|---|---|---|---|
| RBAC | 역할에 권한 묶음. user→role→permission | 단순, 감사 용이 | "본인 것만" 인스턴스 권한 표현 어려움 | ✅ 1차 채택 — 이미 테이블 존재 |
| ABAC | 속성(부서·시간·리소스 태그) 조건 평가 | 유연, 동적 | 정책 폭발, 디버깅 난이도 | △ 일부 조건만 보조 |
| ReBAC | 관계 그래프(Google Zanzibar/OpenFGA) | 계층/위임에 강력, 대규모 | 인프라 추가, 러닝커브 | △ 조직-리소스 위임 커지면 검토 |

### 핵심 발견 (B2B)

1. **역할 스코프와 권한 스코프를 분리한다(GitHub 패턴).** 역할은 조직(Company) 스코프로 부여, 권한 검사는 리소스 스코프로. 전역 Role을 org-scoped로 전환이 핵심.
2. **2단 스코프로 시작(org → resource).** Notion(3단)·Jira(4단)은 우리 규모에 과함.
3. **마스터/일반 = Owner/Member 프리셋.** 미리 정의된 역할 세트로 출발. 커스텀 역할은 후순위.
4. **멤버 추가는 초대(invite) 기반.** 이메일 초대 → 수락 → org membership 생성. pending/accepted 상태.
5. **org-scoped membership 테이블 필요.** 별도 `OrganizationMember(orgId, userId, orgRole)` 엔티티가 깔끔.
6. **ReBAC(OpenFGA)는 지금 도입하지 않되 경계는 남긴다.** 위임이 깊어지면 그때 검토.

---

## 우리 플랫폼 적용 권장 패턴

1. **B2C와 B2B를 분리된 두 축으로 설계.** 한 User가 둘 다 가질 수 있음(개인 소비자이면서 시설업체 직원).
2. **Entitlement를 1급 객체로 신설(Stripe 패턴).** grade → `Entitlement{feature, type, value}` set. 게이팅은 서버 권위. 등급 소스는 구독(멤버십 PRD)·로열티(누적 구매액) 분리.
3. **B2B는 `Organization` + `OrganizationMember`(orgId, userId, orgRole=OWNER/STAFF) 신설.** 전역 `FACILITY_OWNER`를 org-scoped로 이관. 리소스에 `organizationId` 부여.
4. **마스터=OWNER, 일반=STAFF 프리셋 2역할로 시작.** OWNER는 초대/역할 변경/정산/삭제, STAFF는 운영만.
5. **멤버 초대 플로우.** 기존 Notification/이메일 게이트웨이 재사용. pending/accepted/revoked.
6. **권한 검사 일관화.** `AuthorizationGuard`(전역)+`OwnershipGuard`(본인)에 org-scoped 검사 추가한 통합 정책으로. @PreAuthorize 미사용(코드 기반 가드 유지) 일관.

### 참고 URL
- Stripe Entitlements: https://docs.stripe.com/billing/entitlements
- Slack roles: https://slack.com/help/articles/360018112273
- Notion permissions: https://www.notion.so/help/sharing-and-permissions
- GitHub org roles: https://docs.github.com/en/organizations/managing-peoples-access-to-your-organization-with-roles
- Google Zanzibar/OpenFGA: https://openfga.dev/ , https://research.google/pubs/zanzibar-googles-consistent-global-authorization-system/
- Linear: https://linear.app/docs/members-roles
- RBAC vs ABAC vs ReBAC (2024): https://www.permit.io/blog/rbac-vs-abac-vs-rebac
- AWS IAM best practices: https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html
