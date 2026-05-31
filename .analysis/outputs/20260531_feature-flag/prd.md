# 피처 플래그 (Feature Flag) 시스템 PRD

> 작성일: 2026-05-31 | 작성자: biuea3866@gmail.com
> 소스: 무중단 배포(zero-downtime) 기반 — 코드 머지와 기능 노출 분리, 점진 롤아웃·킬 스위치
> 관련 PRD: [B2C/B2B 권한 체계 고도화](../20260531_authz-b2c-b2b/prd.md), [구독 & 멤버십](../20260531_subscription-membership/prd.md) — 두 PRD의 신규 동작이 본 플래그 뒤에서 dark launch
> 벤치마킹 자료: [research-feature-flag-patterns.md](./research-feature-flag-patterns.md)
> prd-reviewer 검수: 2026-05-31 NEEDS_REVISION → Must Fix M1~M4 반영 (하단 검수 이력)

---

## 진행 현황 (Status Ledger)

> 최종 갱신: 2026-05-31 | 전체: 0/7 배포완료 (0%)

| FR | 기능 | 상태 | 구현(PR) | 배포 | 비고 |
|----|------|------|---------|------|------|
| FR-01 | FeatureFlag 도메인 + 평가 규칙(Rich Entity) | ⬜ 대기 | — | — | boolean/%/타게팅, stickiness |
| FR-02 | 영속(MySQL) + 평가 캐시(Redis) + 캐시 무효화 | ⬜ 대기 | — | — | FR-01 선행 |
| FR-03 | Kafka 플래그 변경 전파 (`feature-flag.changed.v1`) | ⬜ 대기 | — | — | 멀티 인스턴스 캐시 동기화 |
| FR-04 | 플래그 평가 진입점 (Gateway/Guard) | ⬜ 대기 | — | — | UseCase/presentation에서 호출 |
| FR-05 | 관리 API + 어드민 UI (생성/토글/롤아웃/킬) | ⬜ 대기 | — | — | ADMIN 전용 |
| FR-06 | % 점진 롤아웃 + 킬 스위치 | ⬜ 대기 | — | — | consistent hashing |
| FR-07 | 플래그 부채(flag debt) lifecycle·정리 | ⬜ 대기 | — | — | 만료일·stale 전이 |

### 상태 정의 (증거 기반 — `rules/COMPLETION-RULE.md` 연계)

| 상태 | 진입 조건 (증거) |
|---|---|
| ⬜ 대기 | 티켓만 존재 |
| 🟡 진행중 | 브랜치 존재 / 커밋 시작 |
| 🔵 구현완료 | dev 머지 PR + COMPLETION-RULE §1~4 충족 |
| ✅ 배포완료 | release 태그 / 배포 로그 |

---

## 배경 (Background)

피처 플래그 시스템이 **존재하지 않습니다**. AS-IS는 코드 대조로 확인했습니다.

| 자산 | 실제 코드 상태 (검증) | 한계 |
|---|---|---|
| 피처 플래그 | **0건** — `togglz`/`unleash`/`FeatureFlag`/`@ConditionalOnProperty` 사용처 없음 | 코드 머지 = 기능 노출. 미완성/위험 코드를 끄고 배포할 수단 없음 |
| Redis | 사용 중 (`RedisDistributedLock`·`SeatLockStoreImpl`·`RedisRefreshTokenRepository`) | 평가 캐시·즉시 동기화 인프라 재사용 가능 |
| Kafka | 사용 중 (`KafkaDomainEventPublisher`·`NotificationEventWorker`) | 플래그 변경 브로드캐스트에 재사용 가능 |
| 저장소 | MySQL(Flyway). 메인 dev 최신 = V33이나 **워크트리/in-flight 브랜치에 V34·V35 이미 존재**(심지어 `V34__add_bookings_slot_capacity_index`·`V34__fix_carts_active_unique` **V34 2개 충돌**, `V35__make_payments_pg_transaction_id_unique`) + MongoDB | "V34부터" 가정 불가 — **단일 발번 큐 필요**(아래 M1) |
| 배포 방식 | (가정) 롤링/재배포 | 기능 노출 제어가 배포에 결합 — 롤백 = 재배포(다운타임) |

→ **코드 머지와 기능 노출이 결합**돼 있어, 미완성 기능을 dev/운영에 올리려면 완성될 때까지 머지를 미뤄야 합니다(big-bang 배포). 장애 시 롤백은 재배포뿐입니다. 본 PRD는 **deploy ≠ release**를 실현하는 피처 플래그 시스템을 신설해, [권한 체계 PRD](../20260531_authz-b2c-b2b/prd.md)·[멤버십 PRD](../20260531_subscription-membership/prd.md) 등 후속 기능이 dark launch·점진 롤아웃·킬 스위치로 무중단 배포되게 합니다.

---

## 벤치마킹 (Benchmarking)

전문은 [research-feature-flag-patterns.md](./research-feature-flag-patterns.md). 핵심 비교:

| 제품/도구 | 종류 | 롤아웃·평가 | 우리가 참고할 점 (차용/변형/미채택) |
|---|---|---|---|
| LaunchDarkly | SaaS | 룰셋 로컬 캐시 in-process 평가 + SSE 스트리밍, Guarded Releases | "deploy≠release", 로컬 캐시 평가 모델 (차용), SaaS 자체는 미채택(외부 의존·비용) |
| Unleash | OSS self-host | gradualRollout%, Edge 캐시, lifecycle(active/stale) | self-host·MurmurHash stickiness·lifecycle (차용), 서버 운영 부담은 변형 |
| Togglz | Java lib | Redis state + actuator 런타임 토글 | MVP 부트스트랩으로 조건부 차용(Hexagonal adapter 뒤에 은닉) |
| `@ConditionalOnProperty` | 빌트인 | 부팅 1회 평가 | 배포 단위 정적 스위치만 (부분 채택), 런타임 % 롤아웃엔 **미채택** |
| Flagsmith/Split/PostHog/GrowthBook | OSS/SaaS | multivariate·실험 통계 | 실험 요구 폭증 시 재검토 (현재 미채택) |

**무중단 배포 패턴**(채택): Dark Launch / % Rollout(consistent hashing) / Kill Switch / expand-contract 마이그레이션 cut-over.

**권장 방향**: SaaS SDK의 "로컬 캐시 평가 + 스트리밍 동기화"를 **우리 인프라(MySQL source of truth + Redis 캐시 + Kafka 전파)로 자체 재현**. SaaS·외부 의존은 데이터 주권·비용 이유로 미채택(거버넌스·실험 요구 폭증 시 재검토). 자세한 차용/변형/미채택 판단은 research 문서 참조.

---

## 목표 (Goals)

- **deploy ≠ release** — 코드 머지(dev/운영 배포) 후에도 기능을 끈 채 둘 수 있음
- 장애 시 **재배포 없이 즉시 차단**(킬 스위치) — 기능 비활성 전파 지연 목표치 설정
- **점진 롤아웃**(% / 세그먼트) — 같은 사용자는 항상 같은 분기(stickiness)
- 멀티 인스턴스 **플래그 상태 일관성** — 변경이 전 인스턴스에 수초 내 반영
- 평가가 **거래 경로 지연에 주는 영향 최소** — 매 평가 네트워크 호출 0(로컬 캐시)
- **플래그 부채 0 누적** — 정착 플래그는 제거 티켓 강제

## 비목표 (Non-Goals)

- 외부 SaaS(LaunchDarkly 등) 도입 — 자체 구현/OSS self-host 우선
- A/B 실험 통계 엔진 — 본 PRD는 릴리스 제어용 토글에 집중(실험은 차기)
- 클라이언트(모바일/웹) 자체 평가 — **서버 권위 평가**, 클라는 서버 결과만 수신
- multivariate(다분기) 플래그 — v1.0은 boolean + % 롤아웃, 다분기는 차기
- 인프라 레벨 배포 전략(블루그린/카나리) 재정의 — 상호 보완이나 본 PRD 범위 밖

---

## 사용자 스토리 (User Stories)

| As a | I want to | So that |
|------|-----------|---------|
| 개발자 | 미완성 기능을 꺼진 채로 머지·배포 | 긴 브랜치 없이 trunk 기반으로 작업 |
| 릴리스 담당 | 기능을 1%→100%로 점진 노출 | 문제를 조기에 작은 범위에서 발견 |
| 운영자 | 장애 기능을 즉시 끄기 | 재배포 없이 다운타임 최소화 |
| 운영자 | 신규 PG/외부 연동을 일부 사용자만 | 위험을 격리 |
| 개발 리드 | 오래된 플래그를 추적·정리 | flag debt 누적 방지 |

---

## 기능 요구사항 (Functional Requirements)

### FR-01. FeatureFlag 도메인 + 평가 규칙 (Rich Entity)
- **결과**: `FeatureFlag` Rich Domain Entity — `key`(`<domain>.<feature>` unique), `enabled`(전역 ON/OFF=킬 스위치), `rolloutPercentage`(0~100), 타게팅 규칙(세그먼트·내부직원 우선 등), `type`(temporary/permanent), `expiresAt`(temporary), 상태(active/potentially-stale/stale). 평가 로직을 Entity 메서드 `isEnabledFor(context)`에 캡슐화 — **consistent hashing(MurmurHash 등)으로 stickiness**(같은 식별자는 항상 같은 분기). 평가 컨텍스트는 **인자로 전달**(Entity는 순수, Repository/캐시 주입 금지).
- **stickiness 키**: 로그인 사용자는 `userId`. **비로그인/익명 경로는 fallback 키 필수** — 세션ID 또는 디바이스/요청 식별자(서버가 부여, 클라 신뢰 금지). fallback 키도 없으면 `rolloutPercentage`와 무관하게 전역 `enabled`만 따른다(요청마다 분기 흔들림 방지).

### FR-02. 영속(MySQL) + 평가 캐시 + 캐시 무효화
- **결과**: `FeatureFlagRepository`(domain interface) → MySQL이 **source of truth**(audit 6컬럼·soft-delete). 캐시 레이어 배치 **확정**(오픈이슈 아님):
  - **레이어**: 캐시는 Entity도 DomainService도 아닌 **infrastructure**에 둔다(Entity 순수성 유지). `FeatureFlagRepository` 구현체(infra)가 **로컬 인메모리 캐시(평가 핫패스, 매 평가 네트워크 0) + Redis(인스턴스 공용 캐시·즉시 무효화)** 2단을 둔다.
  - **평가 진입점 명명**: 외부 시스템이 아니라 자체 저장소 조회이므로 `FeatureFlagGateway`(외부 시스템용 명명)가 **아니라** `FeatureFlagRepository` + 별도 **평가 컴포넌트**(`FeatureFlagEvaluator`, application/domain service)로 분리. Entity의 `isEnabledFor`는 순수 규칙, Evaluator가 조회·캐시·컨텍스트 조립 담당.
  - 무효화: TTL + FR-03 이벤트.

### FR-03. 플래그 변경 전파 (멀티 인스턴스 캐시 무효화)
- **결과**: 플래그 변경(생성/토글/롤아웃%/킬) 시 전 인스턴스 캐시를 무효화한다.
  - **모든 인스턴스가 수신해야 하므로 인스턴스별 고유 컨슈머 그룹** 필요(공유 그룹이면 1개 인스턴스만 소비 → 일관성 깨짐).
  - **킬 스위치 즉시성**(prd.md 목표)과 Kafka rebalance·소비 지연이 상충할 수 있어, **킬 스위치 경로는 Redis pub/sub 즉시 전파**로 분리하고 일반 변경은 `feature-flag.changed.v1`(AFTER_COMMIT, `KafkaDomainEventPublisher` 재사용) 사용을 검토(오픈이슈 #5). 토픽 파티션·DLQ·컨슈머 그룹 설계는 별도 티켓.
  - 전파 지연 목표: 일반 < 수초, 킬 스위치 < 1초 목표.

### FR-04. 플래그 평가 진입점 (Gateway/Guard)
- **결과**: 도메인/애플리케이션이 플래그를 조회하는 단일 진입점 — `FeatureFlagGateway`(domain interface) 또는 평가 컴포넌트. 분기는 **UseCase 또는 presentation 한 곳**에 두고 도메인 깊숙이 흩뿌리지 않음. `/feature`·`/implement` 파이프라인의 게이팅 규칙(신규/위험 동작은 플래그 뒤, 기본 OFF, 서버 권위)과 일치. OFF 시 기존(AS-IS) 동작 유지(회귀 0건).

### FR-05. 관리 API + 어드민 UI
- **결과**: ADMIN 전용 플래그 관리 — 목록·생성·토글·롤아웃% 조정·킬 스위치·만료일 설정. 기존 RBAC(`@PreAuthorize hasRole('ADMIN')` 또는 권한)로 보호. 변경 audit 기록. 어드민 UI(web)에서 조작. 모든 변경은 FR-03 전파 트리거.

### FR-06. % 점진 롤아웃 + 킬 스위치
- **결과**: 롤아웃 % 단계 조정(1→5→10→100), stickiness 보장(FR-01). 킬 스위치 = `enabled=false`로 즉시 전역 차단(롤아웃%와 무관하게 최우선). 외부 Gateway(PG·SMS·푸시) 킬 스위치를 **permanent 운영 토글**로 분류(graceful degradation, 트랜잭션 밖 외부 호출 컨벤션과 일관).

### FR-07b. expand-contract 마이그레이션 cut-over (무중단 스키마 변경)
- **결과**: 파괴적 스키마 변경 시 플래그로 read 경로를 cut-over한다. ① Expand(Flyway로 신·구 구조 병존) → ② 데이터 백필 → ③ read 경로를 플래그로 전환(% ramp, 실패 시 즉시 구 경로 복귀) → ④ 100% 안정 후 Contract(구 구조 제거 마이그레이션). MongoDB는 신·구 필드 병존 후 플래그 전환. 권한 체계 PRD의 organizationId 백필(expand-contract)과 직접 연계.

### FR-07. 플래그 부채(flag debt) lifecycle·정리
- **결과**: temporary 플래그는 생성 시 `expiresAt`·owner 지정. lifecycle 자동 전이(active → 만료 초과 시 potentially-stale → 목적 달성 시 stale). stale 플래그 알림(예: 운영자 인박스/Slack). 정착(100% 안정) 플래그는 **제거 티켓 강제**. permanent(킬 스위치·운영 토글)는 정리 대상 제외. 오래된 플래그 감사 리포트.

---

## 비기능 요구사항 (Non-Functional Requirements)

- **성능**: 플래그 평가는 모든 게이팅 거래 경로에 추가 → **로컬/Redis 캐시로 매 평가 DB 조회 0**, 평가 p99 상한 설정. stickiness 해시는 O(1).
- **가용성**: 플래그 저장소/캐시 장애 시 **fail-safe 방향을 플래그 유형별로 확정**(오픈이슈 아님):
  - **dark-launch 게이트**(신규/위험 동작 노출용): 평가 불가 시 **OFF**로 fail → 미완성 동작 미노출, 기존(AS-IS) 경로 유지.
  - **운영 킬 스위치**(외부 Gateway 차단 등 보호용): 평가 불가 시 **차단 상태 유지(safe)** → 장애 중 위험 동작이 다시 열리지 않게. 즉, "마지막으로 차단했으면 차단 유지".
  - 두 유형은 fail 방향이 **반대**다. 플래그에 `failMode`(FAIL_OFF / FAIL_SAFE_BLOCK) 속성으로 명시.
- **일관성**: 멀티 인스턴스 변경 전파 지연 목표(수초). 같은 사용자 같은 분기(stickiness) 보장.
- **보안**: 플래그 변경은 ADMIN 권위. 평가는 서버 권위(클라 신뢰 금지). 변경 audit.
- **운영**: 플래그별 ON/OFF·롤아웃% 메트릭, stale 플래그 수, 킬 스위치 발동 이력.

---

## 제약 조건 (Constraints)

- Hexagonal + Rich Domain, JPA Entity=Domain Entity 단일 모델, audit 6컬럼 + soft delete.
- domain은 `FeatureFlagRepository`/`FeatureFlagGateway` interface만 정의, 구현은 infrastructure(Redis·Kafka 어댑터).
- `@ManyToMany` 금지, no-db-fk(FK id Long), QueryDSL(`@Query` 금지), ZonedDateTime, 도메인 패키지 간 import 금지.
- 외부 SaaS 미도입(자체 구현 또는 OSS self-host). Togglz 채택 시 infrastructure adapter 뒤에 은닉(domain은 Togglz API 비의존).
- Flyway 번호: 메인 dev는 V33이나 **in-flight 워크트리에 V34·V35가 이미 점유**(V34는 2개 충돌 상태)되어 있고 **권한 체계 PRD도 V34를 주장**한다. 본 PRD는 특정 번호를 고정하지 않고 **머지 시점에 단일 발번 주체가 가용 번호(현재 추정 V36~)를 발급**받는다. 동시 진행 PRD 간 발번 큐 조율 필수.
- 이벤트 발행은 AFTER_COMMIT(`@TransactionalEventListener`).

---

## 영향 범위 (Scope)

| 레포 | 변경 유형 | 설명 |
|---|---|---|
| backend | 신규 | `domain/featureflag/`(FeatureFlag, Repository·Gateway interface), infrastructure(MySQL Repo + Redis 캐시 + Kafka EventWorker), 평가 진입점, 관리 UseCase/Controller, Flyway V34~ |
| backend | 수정 | Kafka 토픽 설정(`feature-flag.changed.v1`), 후속 기능들이 평가 진입점 호출 |
| web (어드민) | 신규 | 플래그 관리 UI (목록·토글·롤아웃·킬·만료) |
| backend / mobile·web (일반 FE) | 신규 | **서버 평가 결과 부트스트랩 endpoint** — FE가 자기 평가 결과를 받는 API(서버 권위, 클라 자체 평가 금지) |
| Kafka | 신설 | `feature-flag.changed.v1` (일반 변경 전파). 킬 스위치는 Redis pub/sub 즉시 전파 별도 검토 |
| 워크플로 커맨드 | 수정 | `/feature`·`/implement`에 플래그 게이팅 플로우 추가(본 작업에 포함) |

데이터 모델: 신규 `feature_flags`(MySQL). Redis 캐시 키 네임스페이스.

---

## 오픈 이슈 (Open Issues)

| # | 질문 | 담당 | 기한 |
|---|------|------|------|
| 1 | 구현 방식 — 자체 FeatureFlag aggregate(권장1) vs Togglz 부트스트랩(권장2) | 기술 리드 | FR-01 전 |
| 2 | ~~평가 캐시 위치~~ → **FR-02에서 확정**(infra 2단: 로컬 인메모리 + Redis). closed | — | — |
| 3 | ~~fail-safe 기본값~~ → **NFR에서 확정**(유형별 FAIL_OFF / FAIL_SAFE_BLOCK). closed | — | — |
| 4 | 타게팅 차원 — userId %만 vs 세그먼트(등급·조직·내부직원) | PO/기술 리드 | FR-01 전 |
| 5 | 전파 — 일반은 Kafka 확정. **킬 스위치를 Redis pub/sub로 분리할지** 최종 결정 | 기술 리드 | FR-03 전 |
| 6 | Flyway 단일 발번 큐 — V34·V35 in-flight + 권한 PRD 경합, 가용 시작 번호(V36~) 확정·발번 주체 지정 | 기술 리드 | 구현 착수 전 |
| 7 | stale 플래그 알림 채널 — 운영자 인박스 vs Slack | PO | FR-07 전 |
| 8 | FE 평가 결과 부트스트랩 endpoint 형태 (단건 조회 vs 전체 번들) | 기술 리드/FE | FR-04 전 |
| 9 | 권한 PRD entitlement 게이팅과 dark-launch flag 게이팅의 **호출 순서·우선순위** (둘 다 막으면 flag OFF 우선) | 기술 리드 | FR-04 전 |

---

## 마일스톤

| 단계 | 내용 | 목표일 |
|------|------|--------|
| v1.0 (코어) | FR-01 + FR-02 + FR-04 + FR-06 — 도메인·캐시·평가 진입점·% 롤아웃/킬 스위치 (단일 인스턴스 기준) | TBD |
| v1.1 (분산) | FR-03 + FR-05 — Kafka 전파(멀티 인스턴스 일관성) + 관리 UI | TBD |
| v1.2 (거버넌스) | FR-07 — flag debt lifecycle·정리·감사 | TBD |

> FR-02(캐시 일관성)·FR-03(전파)이 멀티 인스턴스 핵심 리스크. v1.0은 단일 인스턴스로 시작 가능하나, 운영 다중 인스턴스면 FR-03가 정합성 필수. 본 시스템 완료 후 권한 체계·멤버십 PRD의 신규 동작이 플래그 뒤 dark launch로 무중단 배포.

---

## 검수 이력 (prd-reviewer)

| 일자 | 판정 | Must Fix | 반영 |
|---|---|---|---|
| 2026-05-31 | NEEDS_REVISION | M1 V34·V35 in-flight+권한PRD 경합(V33 가정 무효) / M2 평가 캐시 레이어·명명 미정 / M3 fail-safe 유형별 방향 / M4 익명 stickiness 키 | 4건 전부 반영 — 단일 발번 큐(V36~), 캐시 infra 2단·Evaluator 분리, FAIL_OFF/FAIL_SAFE_BLOCK, 익명 fallback 키. N1(expand-contract FR-07b)·N3(인스턴스별 컨슈머 그룹)·N4(FE 부트스트랩 endpoint)·N5(권한 PRD 게이팅 우선순위 #9) 반영 |

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-31 | 초안 — AS-IS 코드 대조(플래그 0건·Redis·Kafka 존재) + 벤치마킹(웹서치) + /prd 표준 | biuea3866 |
| 2026-05-31 | prd-reviewer Must Fix M1~M4 반영(V발번 큐·캐시 레이어·fail-safe·익명 stickiness) + N1·N3·N4·N5 보강 | biuea3866 |
