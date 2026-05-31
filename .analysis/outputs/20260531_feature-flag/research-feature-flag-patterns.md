# 피처 플래그 시스템 조사 — Kotlin/Spring Hexagonal 백엔드 적용 관점

조사 우선순위: 2024~2026 자료. 무중단 배포·점진적 롤아웃·킬 스위치 축 집중.

## 축 1: 매니지드/오픈소스 피처 플래그 제품 비교

| 이름 | 종류 | 플래그 타입·롤아웃 | 저장/평가 방식 | 우리가 참고할 점 |
|---|---|---|---|---|
| **LaunchDarkly** | SaaS | boolean/multivariate, % 롤아웃, 카나리, 킬 스위치, Guarded Releases(2024 자동 회귀 감지·자동 롤백) | server-side SDK가 **전체 룰셋을 로컬 캐시**로 받아 in-process 평가. SSE **스트리밍**으로 sub-second 전파, polling 대안. Redis/DynamoDB 영속 저장소, 오프라인 시 캐시값 | 서버측 in-process 평가, 스트리밍 vs 폴링, persistent store, "deploy ≠ release" 철학 |
| **Unleash** | OSS(self-host) + 엔터프라이즈 | activation strategy, gradualRollout(%), 킬 스위치 | API 서버 + **SDK 룰셋 로컬 캐시 후 평가**(폴링 기본). **Unleash Edge/Proxy**가 캐시 레이어. 엔터프라이즈 스트리밍 ms 전파 | **self-host 1순위 후보**. Edge=캐시 레이어, MurmurHash consistent hashing stickiness, flag lifecycle(active/potentially-stale/stale) |
| **Flagsmith** | OSS(self-host) + SaaS | boolean/multivariate, A/B/n, % 타게팅·스케줄 | server+client SDK, Edge API 저지연 | self-host + multivariate 대안. deploy/release 분리 가이드 |
| **Split.io (Harness)** | SaaS | boolean/multivariate + 통계 엔진(실험) | server-side 로컬 평가 | 실험·통계 핵심일 때. 우리 PRD엔 과함 |
| **PostHog** | SaaS + OSS | boolean/multivariate, % 롤아웃 | 분석 스위트 일부 | 제품 분석+플래그 한 곳. 백엔드 토글 전용엔 과함 |
| **GrowthBook** | OSS(self-host) | boolean/multivariate, % + Bayesian 실험 | warehouse-native, SDK 로컬 평가 | A/B 실험 강함. 단순 토글엔 무거움 |

**핵심 결론**: SaaS 공통 SDK 모델 = "전체 룰셋 로컬 캐시 → in-process 평가 → 스트리밍/폴링 동기화". 매 평가마다 네트워크 호출 안 함. 장애 시 마지막 캐시값으로 graceful degradation.

## 축 2: 무중단 배포에서 플래그 사용 패턴

| 패턴 | 무엇 | 핵심 동작 | 우리 적용 |
|---|---|---|---|
| **Dark Launch** | 미완성/숨김 기능을 프로덕션 배포하되 비노출 | trunk-based + flag로 main에 미완성 코드 머지·배포, OFF 기본. deploy를 release에서 분리 | 신규 예약/티켓 플로우를 flag 뒤 배포, 내부 사용자만 ON |
| **% Rollout (canary)** | 트래픽 일부 점진 노출 | 1→5→10→100% ramp. **consistent hashing(MurmurHash) stickiness** — 같은 user 항상 같은 분기 | userId 해시 % 롤아웃. 신규 PG 플로우 5%부터 |
| **Kill Switch** | 장애 시 즉시 차단 | flag OFF로 재시작 없이 비활성(circuit breaker). 다운타임 대폭 감소 | 외부 Gateway(SMS/PG/푸시) 장애 시 즉시 차단, 핵심 예약 보호 |
| **Migration 병행 (expand-contract)** | 스키마 파괴적 변경 무중단 | ①Expand(신구 구조 병존) →②Migrate(백필) →③read 경로 flag cut-over(실패 시 즉시 복귀) →④Contract(검증 후 구 구조 제거) | MySQL Flyway expand 선행 → read flag 게이팅 → ramp → contract. flag=즉시 복귀 수단 |

카나리/블루그린 vs 플래그: 전자는 **인프라 레벨**(인스턴스/트래픽), 플래그는 **앱 레벨**(코드 분기). 상호 보완.

## 축 3: Spring/Kotlin 구현 패턴

| 도구 | 종류 | 저장/런타임 변경 | 한계 |
|---|---|---|---|
| **Togglz** | Java lib | Redis/JDBC/파일. **Runtime Console**(actuator)로 재시작 없이 토글. Redis 연동 시 멀티 인스턴스 동기화 | multivariate/실험 약함. 거버넌스 단순 |
| **Unleash Java/Kotlin SDK** | OSS lib + 서버 | 서버 룰셋 로컬 캐시·평가, 폴링/스트리밍 | 서버 인프라 운영 필요 |
| **FF4J** | Java lib | DB/Redis | 커뮤니티 활성도 낮음 |
| **`@ConditionalOnProperty`/Environment** | 빌트인 | application.yml/Config Server. `@RefreshScope`로 부분 갱신 | **런타임 토글 부적합** — 빈 생성 시 1회 평가, 부팅 후 변경 불가. % 롤아웃·타게팅·킬 스위치 즉시성 없음 |

저장소 트레이드오프:
- **MySQL**: 영속·감사 용이, 매 평가 조회 부담 → 캐시 필수
- **Redis**: 멀티 인스턴스 즉시 동기화·저지연 평가 최적
- **Spring Cloud Config**: 중앙화 좋으나 % 롤아웃·타게팅 표현력 부족
- **외부 SaaS**: 운영 부담 0, 거버넌스 강력하나 외부 의존·비용·데이터 주권

런타임 변경+캐시+일관성: 변경은 Redis pub/sub 또는 폴링으로 전 인스턴스 전파, 평가는 로컬 캐시(in-memory) 저지연 + TTL/이벤트 무효화. **우리는 Redis·Kafka 보유 → Kafka로 flag 변경 이벤트 브로드캐스트 → 각 인스턴스 로컬 캐시 갱신**(SaaS 스트리밍 모델을 자체 인프라로 재현).

## flag debt(플래그 부채) 정리 전략

1. **생성 시 만료일·타입 지정** — temporary(만료+cleanup 계획) vs permanent(킬 스위치·운영 토글). prefix·owner 부여
2. **lifecycle 자동 전이** — active → potentially-stale(수명 초과 자동) → stale(목적 달성 수동). stale 시 Slack 알림·빌드 break
3. **DoD에 cleanup 포함** — 제거 계획 없이 flag 추가 금지
4. **정기 감사** — X일 초과 flag 빌드 auto-fail, healthy vs stale 비율 추적
5. **자동 정리 워크플로** — 실행 경로 분석 후 flag 제거 PR 자동 생성

## 우리 플랫폼 적용 권장 패턴 (Kotlin/Spring Hexagonal, Redis·Kafka 보유, SaaS 신중)

### 권장 1 — 자체 구현 FeatureFlag aggregate + Redis 캐시 + Kafka 전파 [차용·변형]
- **차용+변형 채택.** SaaS SDK "로컬 캐시 평가 + 스트리밍 동기화"를 우리 인프라로 재현.
- domain: `FeatureFlag` Rich Entity(타입·상태·롤아웃%·타게팅·만료일), `FeatureFlagRepository`(MySQL). 평가는 Entity 메서드 `isEnabledFor(context)`에 캡슐화 — MurmurHash stickiness.
- infrastructure: MySQL=source of truth, Redis=평가 캐시, **Kafka `feature-flag.changed.v1`로 전 인스턴스 캐시 무효화**(presentation EventWorker → UseCase, AFTER_COMMIT).
- 트레이드오프: 구현 비용 vs 데이터 주권·기존 스택 재사용·외부 의존 0.

### 권장 2 — Togglz를 MVP 부트스트랩으로 [조건부 차용]
- **초기엔 차용 검토, 장기엔 권장 1로 흡수.** Togglz + Redis state repo + actuator console로 런타임 토글·멀티 인스턴스 동기화를 빠르게 확보.
- 변형: Hexagonal 순수성 위해 Togglz를 infrastructure adapter 뒤에 숨기고 domain은 `FeatureFlagGateway` interface만 의존.
- 리스크: multivariate·타게팅 표현력 한계 → 요구 커지면 권장 1로 마이그레이션 전제.

### 권장 3 — `@ConditionalOnProperty`/Environment는 "배포 단위 정적 스위치"로만 [부분 채택]
- 부팅 시 1회 결정 모듈 on/off에만. **런타임 % 롤아웃·킬 스위치엔 미채택**(빈 생성 시점 고정).

### 권장 4 — expand-contract + flag cut-over를 마이그레이션 표준으로 [차용]
- MySQL 파괴적 변경: Flyway expand 선행 → read 경로 flag 게이팅 → 5%부터 ramp → 100% 후 contract. flag=재배포 없는 즉시 복귀.
- MongoDB는 스키마리스라 신·구 필드 병존 후 flag로 read 전환.

### 권장 5 — 외부 Gateway 킬 스위치를 1급 패턴으로 [차용]
- PG·SMS·푸시 Gateway 호출을 flag로 감싸 장애 시 즉시 차단. 외부 호출은 `@Transactional` 밖이므로 OFF 시 graceful degradation으로 핵심 예약 보호.
- 운영 토글(permanent)로 분류 → cleanup 대상 제외.

### SaaS vs self-host 트레이드오프
| | self-host(권장 1·2) | SaaS |
|---|---|---|
| 데이터 주권·외부 의존 | ✅ 의존 0 | ❌ 외부 의존 |
| 운영 부담 | ❌ 직접 | ✅ 0 |
| 거버넌스·자동 cleanup·실험 | ❌ 직접 구축 | ✅ 강력 |
| 비용 | ✅ 인프라 재사용 | ❌ seat/MAU 과금 |
| 결론 | **1순위(신중 도입 방침 부합)** | 거버넌스·실험 폭증 시 재검토 |

권장 순서: 권장 2(Togglz 빠른 검증) 또는 권장 1(자체 구현) 택1 시작 → 권장 4·5 동반 표준화 → flag debt 전략 동시 적용.

## Sources
- LaunchDarkly vs Unleash: https://launchdarkly.com/compare/launchdarkly-vs-unleash/
- LaunchDarkly SDK client vs server: https://launchdarkly.com/docs/sdk/concepts/client-side-server-side
- OpenFeature server-side SDK 아키텍처: https://openfeature.dev/blog/feature-flags-sdks-architectures/
- LaunchDarkly Dark Launching: https://launchdarkly.com/blog/guide-to-dark-launching/
- LaunchDarkly Guarded Releases(2024): https://launchdarkly.com/blog/launch-week-2024-introducing-guarded-releases/
- Flagsmith deploy/release 분리: https://www.flagsmith.com/blog/decoupling-deployment-from-release-with-feature-flags
- JavaCodeGeeks Spring Boot Unleash vs Togglz: https://www.javacodegeeks.com/2025/04/feature-flags-in-spring-boot-unleash-vs-togglz.html
- Togglz Spring Boot starter: https://www.togglz.org/documentation/spring-boot-starter.html
- Asimio Togglz + Spring Cloud Config: https://tech.asimio.net/2020/11/12/Refreshing-Feature-Flags-using-Togglz-and-Spring-Cloud-Config-Server.html
- Unleash Technical Debt: https://docs.getunleash.io/concepts/technical-debt
- Unleash Stickiness: https://docs.getunleash.io/concepts/stickiness
- Unleash Edge: https://docs.getunleash.io/unleash-edge
- LaunchDarkly 기술 부채: https://launchdarkly.com/docs/guides/flags/technical-debt
- Expand and Contract 패턴: https://www.tim-wellhausen.de/papers/ExpandAndContract/ExpandAndContract.html
- DeployHQ 무중단 DB 마이그레이션: https://www.deployhq.com/blog/database-migration-strategies-for-zero-downtime-deployments-a-step-by-step-guide
- ConfigCat Feature Flag Retirement: https://configcat.com/blog/2024/01/30/feature-flag-retirement/
- Statsig 플래그 부채 관리: https://www.statsig.com/perspectives/feature-flag-debt-management
