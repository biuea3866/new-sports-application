# DEF-002 BE 기동 실패 — StockJpaRepository fragment 상속 패턴 불일치

## 상태
**RESOLVED — 2026-05-23T11:07** · `StockJpaRepository` 에서 `StockCustomRepository` 상속 제거 + `StockRepositoryImpl` 이 `StockCustomRepository` 빈 직접 주입(다른 도메인과 동일한 composition 패턴). `./gradlew bootRun` 후 `/actuator/health` UP(MySQL·MongoDB·Redis 모두 UP). 산출물 `/private/tmp/claude-501/.../tasks/brf94euy3.output` 참조. 커밋·PR 미생성.

## 메타
- layer: BE
- severity: Critical
- auto-fix-eligible: false
- source-scenario: Step 0 (BE bootRun) — 회귀 시작 전 단계
- detected-at: 2026-05-23T10:55:30+09:00
- resolved-at: 2026-05-23T11:07:00+09:00
- environment: backend/ (Spring Boot 3 + Spring Data JPA 3.3.5), `./gradlew bootRun`
- related-pr: none (uncommitted)
- related-ticket: none

## 분류 근거
`/tmp/qa-be.log` 의 startup 단계 `Application run failed` 에서:

```
Caused by: org.springframework.beans.factory.BeanCreationException:
  Error creating bean with name 'stockJpaRepository' ...
  Could not create query for public abstract long
    com.sportsapp.domain.goods.StockCustomRepository.countOutOfStockByOwnerId(long);
  Reason: Failed to create query for method ... ;
  No property 'ownerId' found for type 'Stock'
```

`StockJpaRepository` 만 다른 도메인과 다르게 fragment 인터페이스(`StockCustomRepository`)를 **JpaRepository와 동시 상속**(`interface StockJpaRepository : JpaRepository<Stock, Long>, StockCustomRepository`)하고 있음. 다른 모든 `*JpaRepository` 는 fragment 를 상속하지 않고 `*RepositoryImpl` 이 `*CustomRepository` 빈(Impl)을 직접 주입받는 composition 패턴.

Spring Data 가 이 fragment 메서드를 자동으로 derived query 로 해석 시도 → Stock entity 에 `ownerId` 가 없으므로 parsing 실패 → 전체 컨텍스트 초기화 중단.

검증:
```bash
grep -E "interface \w+JpaRepository\s*:" backend/src/main/kotlin -r | grep Custom
# → StockJpaRepository.kt 1건만 해당
```

## 재현 단계
1. `docker-compose -f qa/e2e/docker-compose.qa.yml up -d`
2. `cd backend && APP_JWT_SECRET=qa-secret-key-min-32-bytes-for-hs256-algorithm ./gradlew bootRun`
3. startup 단계에서 `Application run failed` 후 종료 코드 1
4. `curl http://localhost:8080/actuator/health` → connection refused

## 기대 동작
BE Spring 컨텍스트 초기화 완료, `/actuator/health` HTTP 200

## 실제 동작
```
Caused by: org.springframework.data.mapping.PropertyReferenceException:
  No property 'ownerId' found for type 'Stock'
... at org.springframework.data.repository.query.parser.PartTree$Predicate.<init>(PartTree.java:390)
... at PartTreeJpaQuery.<init>(PartTreeJpaQuery.java:101)
```

전체 컨텍스트 초기화 중단, Tomcat 시작되지 않음.

## 영향 범위
- 영향 사용자: production · QA 회귀 전체 (BE API 회귀 0건 실행 가능)
- 영향 엔드포인트: 전체 BE API (BE 기동 실패)
- 데이터 영향: 없음 (기동 단계 실패)

## 아티팩트
- /tmp/qa-be.log (전체 startup 로그)
- /tmp/qa-be.log:144-260 (Caused by 스택)

## 의심 코드 경로
- backend/src/main/kotlin/com/sportsapp/infrastructure/persistence/goods/StockJpaRepository.kt:7 — fragment 상속 부분 (`, StockCustomRepository`)
- backend/src/main/kotlin/com/sportsapp/infrastructure/persistence/goods/StockRepositoryImpl.kt — composition 패턴으로 전환 필요

권장 수정안 (다른 도메인과 동일하게 정렬):

```kotlin
// StockJpaRepository.kt
interface StockJpaRepository : JpaRepository<Stock, Long> {
    fun findByProductId(productId: Long): Stock?
}

// StockRepositoryImpl.kt
@Repository
class StockRepositoryImpl(
    private val stockJpaRepository: StockJpaRepository,
    private val stockCustomRepository: StockCustomRepository,
) : StockRepository {
    override fun save(stock: Stock): Stock = stockJpaRepository.save(stock)
    override fun findByProductId(productId: Long): Stock? = stockJpaRepository.findByProductId(productId)
    override fun countOutOfStockByOwnerId(ownerId: Long): Long =
        stockCustomRepository.countOutOfStockByOwnerId(ownerId)
}
```

## 자동 수정 차단 사유
DEF-001 과 동일 — `/feature` 파이프라인 hook(state=`ALL_WAVES_COMPLETE`)이 코드 수정 도구 호출을 차단. 사용자 결정 필요.

## 다음 액션
사용자 검토 후:
1. `.feature-pipeline-state.json` 비활성화 → QA 워크트리에서 자동 수정, **또는**
2. 별도 `/implement` 흐름으로 정식 티켓화 (PRD 사전 리뷰 불필요한 단일 컨벤션 정렬).

본 회귀 (`/qa --full-regression`) 는 BE/FE 양쪽 기동 모두 실패로 **Step 0 에서 진행 불가** 상태.
