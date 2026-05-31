# BE 패키지 구조 마이그레이션 계획

> 대상: `backend/src/main/kotlin/com/sportsapp` (706 파일) + 대응 테스트
> 목표 구조: be-code-convention.md `## 패키지 구조 (Package Layout)` (이번에 신설)
> 성격: **동작 불변 구조 리팩토링** — 패키지/디렉토리 이동 + `package` 선언 변경 + import 전수 갱신 + (도메인별) Rich Domain 정리

## 1. 변경 요약 (AS-IS → TO-BE)

| 레이어 | AS-IS (flat) | TO-BE |
|---|---|---|
| presentation/<도메인> | Controller+Request 혼재 | controller / worker / scheduler / batch + dto/request / dto/response |
| application/<도메인> | UseCase+Command+Response+Result 혼재 | usecase + dto (Command·Result) |
| domain/<도메인> | Entity+Repo·Gateway interface+Event+Exception+Enum+Service 혼재 | service / entity / vo / dto / repository / gateway / event / exception |
| infrastructure | 기술 우선 persistence/<도메인>, messaging, redis/<도메인> | 도메인 우선 <도메인>/{mysql,mongo,kafka,gateway} + cross-cutting(config/security/messaging/redis/storage/lock/audit/external) |

추가: Response 가 application → **presentation/<도메인>/dto/response** 로 이동 (UseCase 는 Result 반환, Controller 가 Response 변환).

## 2. 파일 분류 규칙 (filename suffix → target sub-package)

| 매칭 | 이동 위치 |
|---|---|
| `*ApiController.kt` | presentation/<d>/controller |
| `*EventWorker.kt`, `@KafkaListener`/`@TransactionalEventListener` 보유 | presentation/<d>/worker |
| `*Scheduler.kt` (`@Scheduled`) | presentation/<d>/scheduler |
| `*Request.kt` | presentation/<d>/dto/request |
| `*Response.kt` | presentation/<d>/dto/response (※ application → presentation 이동) |
| `*UseCase.kt` | application/<d>/usecase |
| `*Command.kt`, `*Result.kt` | application/<d>/dto |
| `*DomainService.kt` | domain/<d>/service |
| `@Entity` 보유 + Status enum | domain/<d>/entity |
| `@Embeddable` / 순수 enum | domain/<d>/vo |
| 쿼리 projection (Repository 반환 DTO) | domain/<d>/dto |
| `interface *Repository` | domain/<d>/repository |
| `interface *Gateway` | domain/<d>/gateway |
| `*Event.kt` (DomainEvent) | domain/<d>/event |
| `*Exception.kt` | domain/<d>/exception |
| `*JpaRepository.kt`, `*QueryDslRepository.kt`, `*RepositoryImpl.kt` (MySQL) | infrastructure/<d>/mysql |
| Mongo 구현 | infrastructure/<d>/mongo |
| `*GatewayImpl.kt` | infrastructure/<d>/gateway |
| Kafka Producer/Publisher 구현 | infrastructure/<d>/kafka |

분류 모호 항목(수기 판단 필요):
- domain/<d> 의 enum 이 aggregate 상태(`canTransitTo`)인지 순수 분류인지 → entity vs vo
- `*Repository.kt` 가 interface(domain)인지 Impl(infra)인지 — 내용으로 구분
- infrastructure 의 cross-cutting(messaging/config/external/security/redis/storage/lock/audit)은 **이동하지 않음**

## 3. 핵심 리스크 — import 결합

도메인 A 클래스의 패키지 경로가 바뀌면 `import com.sportsapp.domain.A.Foo` → `...domain.A.entity.Foo` 로 **A 를 import 하는 모든 파일**이 같은 커밋에서 갱신돼야 컴파일이 유지된다.

크로스 도메인 결합 (측정):

| 도메인 | 이 도메인을 import 하는 곳 |
|---|---|
| payment | booking, goods, ticketing, mcp, infra(16) |
| booking | facility, mcp, infra(21) |
| facility | mcp, infra |
| user | facility, mcp, infra(28) |
| notification | mcp, infra(33) |
| mcp | infra(50) — 허브 소비자, 자신은 7개 도메인 import |
| operator/post/message/weather | infra 일부 |

→ **mcp·infrastructure 가 거의 모든 도메인의 공통 importer.** 따라서 "도메인별 병렬 wave" 는 mcp/infra 파일에서 single-writer 충돌이 난다. 순수 병렬 분해 불가.

## 4. 실행 방식 — 2가지 안

### 방안 A — 스크립트 1회 + 단일 PR (권장)

이동·import 갱신이 100% 결정적이므로 변환 스크립트로 한 번에 처리.

1. 분류 스크립트 작성: 각 .kt 의 (현재경로, 파일명, 내용)으로 target subpackage 산출 → 이동 맵 생성
2. `git mv` 전체 + `package` 선언 일괄 치환 + `import com.sportsapp.*` 전수 치환 (이동 맵 기반 sed)
3. `./gradlew compileKotlin compileTestKotlin` GREEN 확인 → 실패 시 매핑 보정 반복
4. `./gradlew test` GREEN
5. detekt/ktlint 통과
6. 단일 PR (`refactor/GRT-XXXX-be-package-layout`)

- 장점: 중간 컴파일 깨짐 없음(원자적), 사람 실수 최소, 일관성 100%
- 단점: PR diff 거대(700+ 파일) → 리뷰는 "스크립트 + 컴파일 GREEN + 테스트 GREEN" 으로 대체. 로직 diff 0 을 `git diff --stat` 와 import-only 검증으로 입증
- Rich Domain 정리(아래 §5)는 **별도 후속 PR** 로 분리 (구조 이동과 로직 변경 섞지 않음)

### 방안 B — 도메인 순차 PR (leaf-first)

의존 그래프 위상정렬로 한 번에 한 도메인씩, 각 PR 이 그 도메인 이동 + 전 importer import 갱신 포함.

순서(leaf → hub): weather → post → message → operator → payment → user → notification → booking → facility → goods → ticketing → mcp

- 장점: PR 단위 작음, 리뷰 용이
- 단점: 13개 순차 PR(병렬 불가, importer 충돌), 각 PR 이 타 도메인 파일 import 라인 수정 → 리뷰 노이즈, 총 소요 김. 중간 상태는 각 PR 내에서만 green.

> 권장: **방안 A**. 순수 기계적 이동은 스크립트 + 컴파일/테스트 게이트가 사람 분해보다 안전. CLAUDE.md '단순성 우선' 과 ticket-guide '단순 디렉토리 이동 티켓 금지'(=로직 정리 동반)와 충돌하지 않도록, 구조 이동(A)과 Rich Domain 정리(§5)를 PR 로 분리.

## 5. 동반 Rich Domain 정리 (구조 이동과 분리된 후속 PR)

구조 이동만으로 끝내면 ticket-guide 의 "단순 디렉토리 이동 금지" 에 걸린다. 코드 스캔에서 발견된 실제 위반을 후속 PR 로 함께 처리:

| 위반 | 위치 | 조치 |
|---|---|---|
| DomainService private 검증 helper | `domain/goods/CartDomainService.kt:75-89` (`requirePositiveQuantity`/`validateProductActive`/`validateStockSufficient`) | Product/Stock/CartItem Entity 메서드로 이동 |
| DomainService 검증 helper | `domain/goods/GoodsDomainService.kt:81-91` (`validateAndDeductStock`) | Stock.requireSufficient + deduct 로 |
| Anemic Entity | `domain/user/Role.kt`, `UserRole.kt`, `RolePermission.kt`, `domain/ticketing/Seat.kt`, `domain/goods/GoodsOrderItem.kt` | 비즈니스 메서드 1개+ 추가 또는 vo 재분류 |
| UseCase 내 검증 | `application/goods/SearchProductsUseCase.kt:28`, `application/message/CreateRoomUseCase.kt:18` | DomainService/Entity 로 이동 |
| DomainService 메서드 >15줄 | `GoodsDomainService.createPendingOrder()` 32줄, `TicketingDomainService.createEvent()` 22줄 | 분리/Entity factory 위임 |

## 6. harness-rules / 도구 영향

이번에 이미 반영:
- `no-persistence-entity-pojo`, `no-domain-persistence-mapper` glob → 신경로(`*/infrastructure/*/{mysql,mongo}/**`) 포함
- `no-repository-in-consumer` glob → `*EventWorker*.kt` (파일명 기반, 훅 실효)
- `package_layout` 블록 신설 (PR 리뷰어/auditor 참조)

미결 — 결정 필요:
- **harness-check.py file_glob 파일명-only 매칭**: 경로 기반 구조 규칙은 PreToolUse 훅에서 발동 안 함. 훅을 전체경로 매칭으로 고칠지(블래스트 반경: forbidden_patterns 내 경로 glob 룰 활성화), PR 리뷰어 강제만 둘지 결정 필요.
- `repository_naming` 블록은 타 프로젝트 템플릿 잔재(`<WORKER_SERVICE>`, ApplicantDowngradeService) — 본 레포 기준으로 재작성할지 별도 판단.

## 7. 검증 기준 (완료 단언 조건 — COMPLETION-RULE §2)

- `./gradlew compileKotlin compileTestKotlin` BUILD SUCCESSFUL raw 출력
- `./gradlew test` BUILD SUCCESSFUL raw 출력
- `git diff` 가 package/import/경로 외 로직 변경 0 (구조 PR 한정)
- 구 평면 잔재 0: `find domain -maxdepth 2 -name '*.kt' | grep -vE '/(service|entity|vo|dto|repository|gateway|event|exception)/'` → 0 (common 제외)
- `find infrastructure -path '*persistence*' -name '*.kt' | wc -l` → 0
