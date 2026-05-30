# DEF-001 BE startup blocker — StockCustomRepository.countOutOfStockByOwnerId Spring Data query derivation 실패

## 메타
- layer: BE
- severity: Critical
- auto-fix-eligible: true
- source-scenario: (없음 — `/qa --full-regression` Step 0 환경 기동에서 발견)
- detected-at: 2026-05-22T01:00:23+09:00
- environment: docker-compose.qa.yml (commit `2999d68`, origin/dev)
- related-pr: #104 (feat/qa-pipeline — 본 결함과 무관, dev에 사전 존재)
- related-ticket: none

## 분류 근거
- `/tmp/qa-be.log`의 ERROR 라인 (artifacts/be-startup-error.log 30줄 발췌):
  > `org.springframework.data.mapping.PropertyReferenceException: No property 'ownerId' found for type 'Stock'`
  > `Could not create query for public abstract long com.sportsapp.domain.goods.StockCustomRepository.countOutOfStockByOwnerId(long)`
- `backend/src/main/kotlin/com/sportsapp/domain/goods/Stock.kt`에 `ownerId` 필드 없음 — `productId`, `quantity`, `version`만 존재
- Spring Data JPA가 `StockCustomRepository.countOutOfStockByOwnerId`를 **fragment custom impl로 인식하지 못하고 query method derivation으로 처리** → `Stock` 엔티티에서 `ownerId` 속성을 찾다가 실패
- BE bootRun이 ApplicationContext 시작 단계에서 실패 → 모든 API 5xx 이전에 **프로세스 자체가 안 뜸** → layer: BE / Critical

## 재현 단계
1. `docker-compose -f qa/e2e/docker-compose.qa.yml up -d` (인프라 기동)
2. `qa/e2e/wait-for-healthy.sh` — 5개 컨테이너 healthy 확인
3. `cd backend && APP_JWT_SECRET="..." ./gradlew bootRun`
4. **35초 후 BUILD FAILED** — Tomcat context 초기화 실패

## 기대 동작
BE가 :8080에서 listen하고 `/actuator/health`가 200 응답.

## 실제 동작
ApplicationContext 시작 중 `stockJpaRepository` bean 생성 실패:

```
QueryCreationException: Could not create query for method
  public abstract long com.sportsapp.domain.goods.StockCustomRepository.countOutOfStockByOwnerId(long)
Caused by: PropertyReferenceException: No property 'ownerId' found for type 'Stock'
```

연쇄 실패 — `goodsDomainService` → `getMyDashboardSummaryUseCase` → Tomcat context refresh 취소.

전체 스택트레이스 약 100줄 (artifacts/be-startup-error.log 30줄 발췌).

## 영향 범위
- 영향 사용자: 전체 — BE 자체가 안 떠서 모든 화면 ·API 실패
- 영향 화면/엔드포인트: 모두
- 데이터 영향: 없음 (기동 단계 실패라 데이터 read/write 없음)

## 아티팩트
- [be-startup-error.log](../artifacts/be-startup-error.log) — ERROR 본문 30줄

## 의심 코드 경로

총 6개 파일이 같은 메서드 시그니처를 참조 — fragment 패턴 구성에서 모호함이 발생한 듯:

| 파일 | 라인 | 역할 |
|---|---|---|
| `backend/src/main/kotlin/com/sportsapp/domain/goods/Stock.kt` | 14-22 | Entity — `productId`/`quantity`/`version`만 보유, `ownerId` **없음** |
| `backend/src/main/kotlin/com/sportsapp/domain/goods/StockRepository.kt` | 6 | 도메인 Repository interface — `countOutOfStockByOwnerId` 선언 |
| `backend/src/main/kotlin/com/sportsapp/domain/goods/StockCustomRepository.kt` | 3-4 | Custom fragment interface — 같은 메서드 **중복 선언** |
| `backend/src/main/kotlin/com/sportsapp/infrastructure/persistence/goods/StockJpaRepository.kt` | 7 | `JpaRepository<Stock, Long>, StockCustomRepository` extends |
| `backend/src/main/kotlin/com/sportsapp/infrastructure/persistence/goods/StockCustomRepositoryImpl.kt` | 12, 20 | fragment impl (QueryDSL 사용?) |
| `backend/src/main/kotlin/com/sportsapp/infrastructure/persistence/goods/StockRepositoryImpl.kt` | 17-18 | 도메인 Repository 구현, JpaRepository에 위임 |

가설:
- A) `StockJpaRepository`가 `StockCustomRepository`를 extends하지만 Spring Data가 `StockCustomRepositoryImpl`을 fragment로 인식하지 못함 → query derivation으로 fallback → 실패
- B) Spring Data JPA의 fragment 명명 규칙(Custom interface는 `~CustomImpl`을 찾음). 본 케이스는 인터페이스가 `StockCustomRepository`라 impl이 `StockCustomRepositoryImpl`로 일치 — 명명은 OK. 다른 원인일 가능성
- C) `StockRepository`(도메인 인터페이스)에도 같은 메서드가 있어서 fragment 위임 경로가 충돌

근본 원인 진단·수정은 be-implementer가 결정.

## 자동 수정 지시
대상 에이전트: be-implementer
작업 범위:
- 결함 한정 — `Stock` 엔티티 / `StockRepository` / `StockCustomRepository` / `StockJpaRepository` / `StockCustomRepositoryImpl` / `StockRepositoryImpl` 6개 파일 범위 내에서 수정. 인접 도메인(`GoodsDomainService`, `GetMyDashboardSummaryUseCase`) 리팩토링 금지 (CLAUDE.md §3 정밀한 수정).
- TDD 사이클:
  1. **RED**: `@DataJpaTest` + Testcontainers MySQL로 `StockJpaRepository.countOutOfStockByOwnerId(ownerId)`가 정상 호출되는 테스트 작성 — 이 테스트가 현재 startup 단계에서 실패해야 함
  2. **GREEN**: 가장 가능성 높은 fix — `StockCustomRepository`의 메서드를 Spring Data fragment custom impl로 제대로 등록 (또는 `StockRepository` 도메인 메서드를 호출부에서 직접 사용하도록 정리). 핵심은 query derivation 경로에서 빠지게 하기.
  3. **GREEN 검증**: `./gradlew bootRun`이 정상 startup 후 :8080 listen
- 테스트 위치 제안: `backend/src/test/kotlin/com/sportsapp/infrastructure/persistence/goods/StockRepositoryQueryTest.kt`
- 예상 변경 파일 수: 2~3개 (위 6개 중 일부)
- **반드시 점검**: 같은 fragment 패턴 안티패턴이 다른 도메인에도 있는지 grep으로 확인:
  ```bash
  grep -rn "interface.*CustomRepository" backend/src/main/kotlin --include="*.kt"
  ```
