# BE 코드 컨벤션 (Kotlin / Spring Boot)

Hexagonal Architecture + Rich Domain Model을 기반으로 하는 BE 실전 컨벤션. `be-implementer`, `be-senior`, `be-tech-lead`, `pr-reviewer`가 공통 참조한다.

## 레이어 책임

| 레이어 | 책임 | 허용 의존 | 금지 |
|---|---|---|---|
| **presentation** | 라우팅(Controller), 인증, Request→Command 변환, UseCase 호출, **Kafka Consumer/EventListener** (외부 이벤트 진입점) | application | 비즈니스 로직 |
| **application** | UseCase 단위 오케스트레이션 | domain | Repository/Gateway/DomainEventPublisher 직접 참조, 비즈니스 로직 |
| **domain** | 비즈니스 로직 (Rich Domain Model) + **JPA Entity 매핑**, Repository·Gateway·DomainEventPublisher **interface 정의** | `jakarta.persistence.*`, `org.springframework.data.annotation.*`, JPA/Mongo 매핑 어노테이션만 (구현체 import 금지) | Infrastructure 구현체 참조, 다른 도메인 패키지 import |
| **infrastructure** | Domain interface 구현체 (Repository/Gateway/DomainEventPublisher), 기술 어댑터 (Kafka·외부 API), QueryDSL CustomRepositoryImpl | domain | — |

> **OutputPort 패턴은 사용하지 않습니다.** Domain layer에는 Repository / Gateway / DomainEventPublisher interface를 직접 정의하고, infrastructure가 구현합니다.

### JPA Entity = Domain Entity (단일 모델, POJO 분리 금지)

**JPA Entity 가 곧 도메인 Entity 다.** 영속화용 `~Entity.kt`(POJO) 와 도메인 모델을 별도 클래스로 분리하지 않는다.

| 항목 | 규칙 |
|---|---|
| 클래스 위치 | `domain/<context>/<Aggregate>.kt` 한 곳. `@Entity` + `@Table` + 비즈니스 메서드가 같은 클래스 |
| JPA 기능 | `@Entity`, `@Table`, `@Column`, `@Embedded`, `@OneToMany`(같은 aggregate 내), `@Version`(낙관락), `@Convert`, lifecycle callback 등 사용 허용. **단 `@ManyToMany` 는 금지** (아래 참조) |
| domain import 예외 | `jakarta.persistence.*`, `org.springframework.data.annotation.*`(@CreatedDate 등), `org.hibernate.annotations.*`(필요 시) **만** 허용. `infrastructure.*` / 다른 `domain.<other>` import 는 여전히 금지 |
| 영속화 분리 클래스 | `infrastructure/persistence/<context>/<X>Entity.kt` 같은 **별도 POJO 영속화 모델 생성 금지**. 매퍼(toEntity/toDomain) 도 만들지 않는다 |
| Repository 반환 타입 | 도메인 Entity 직접 반환. `JpaRepository<UserEntity, Long>` 가 아니라 `JpaRepository<User, Long>` |
| Aggregate 경계 | 다른 도메인 데이터는 **연관 객체 참조 금지, FK id(Long) 만 보유** (`@ManyToOne User` ❌ → `val userId: Long` ✅). aggregate 내부 자식만 `@OneToMany`/`@Embedded` 허용 |
| **@ManyToMany 금지** | 다대다 관계는 **매핑 테이블을 독립 Entity 로 풀어서** 명시한다. `@ManyToMany` / `@JoinTable` 사용 금지 — 매핑 Entity 가 자체 PK·audit·추가 컬럼(부여 시각, 부여자 등)을 가질 수 있어야 한다 |
| audit/soft-delete | `JpaAuditingBase` 상속 (`class User : JpaAuditingBase()`), MongoDB 는 `BaseDocument` |
| 순수성 트레이드오프 | "domain 은 어떤 것도 import 안 한다" 원칙을 **JPA 매핑 어노테이션에 한해 완화**한다. ORM 매핑은 도메인 영속성의 일부로 간주. 단 *동작*은 여전히 순수 — Repository/Gateway 를 Entity 에 주입하지 않는다 |

**왜 이 결정인가**: POJO 도메인 ↔ JPA Entity 이중 모델은 매퍼 보일러플레이트, 동기화 버그, lazy-loading 경계 혼란을 유발한다. 단일 모델로 Rich Domain + JPA 를 동시에 취한다. 트레이드오프(도메인의 ORM 결합)는 수용한다.

### @ManyToMany 금지 — 매핑 Entity 로 풀어쓰기

다대다는 `@ManyToMany` / `@JoinTable` 로 숨기지 않고 **매핑 테이블을 1급 Entity 로 노출**한다.

이유:
- 매핑에 부여 시각·부여자·만료·활성 여부 같은 컬럼이 거의 항상 추가된다 (`@ManyToMany` 는 이를 못 담는다)
- 매핑 Entity 도 audit/soft-delete (`JpaAuditingBase`) 를 가져야 한다
- 양방향 `@ManyToMany` 의 cascade·orphan·중간 테이블 자동 생성은 디버깅 난이도가 높다
- 매핑 자체에 대한 조회/페이징/정렬이 필요해진다

```kotlin
// ❌ BAD — @ManyToMany
@Entity
class User(...) : JpaAuditingBase() {
    @ManyToMany
    @JoinTable(name = "user_roles", ...)
    val roles: MutableSet<Role> = mutableSetOf()
}

// ✅ GOOD — 매핑 테이블을 독립 Entity 로
@Entity
@Table(name = "user_roles")
class UserRole(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "role_id", nullable = false)
    val roleId: Long,
    @Column(name = "granted_by")
    val grantedBy: Long?,
) : JpaAuditingBase() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0
}

// User 는 매핑 객체 컬렉션을 직접 참조하지 않는다.
// "user 의 role 목록" 은 UserRoleRepository.findActiveByUserId(userId) 같은
// 명시 쿼리로 조회 (aggregate 경계: FK id 만 보유 원칙과 일관).
```

- 매핑 Entity 의 unique 제약: `UNIQUE (user_id, role_id, deleted_at)` 같이 soft-delete 와 공존하도록 설계
- 매핑 Entity 도 `V*__create_user_roles.sql` 에 audit 6 컬럼 + `INDEX idx_user_roles_user_id` 등 포함

```kotlin
// ✅ domain/user/User.kt — JPA Entity 이자 도메인 Entity
@Entity
@Table(name = "users")
class User(
    @Column(name = "email", nullable = false, unique = true)
    val email: String,
    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: UserStatus,
) : JpaAuditingBase() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    fun changePassword(raw: String, encoder: PasswordEncoder) {
        require(status == UserStatus.ACTIVE) { "inactive user" }
        passwordHash = encoder.encode(raw)   // 동작은 순수 — 외부 빈 주입 아님, 인자로 전달
    }
}

// ❌ infrastructure/persistence/user/UserEntity.kt + UserMapper — 생성 금지
```

## UseCase 규칙 (핵심)

### 원칙
1. UseCase 1개 = 행위 1개 = 클래스 1개
2. `execute()` 10줄 이내
3. `@Transactional`은 UseCase에 선언
4. **DomainService만 호출** — Repository/Gateway/EventPublisher 직접 참조 절대 금지
5. 비즈니스 로직(검증/상태전이/계산) 금지 — Entity/DomainService 위임

### 안티 패턴 (하네스가 차단)

```kotlin
// ❌ BAD — UseCase가 Repository 직접 호출 + if+throw 나열
class RequestRentalUseCase(
    private val productRepository: ProductRepository,  // 차단 no-repo-in-usecase
    private val rentalRepository: RentalRepository,
) {
    @Transactional
    fun execute(command: RequestRentalCommand): RequestRentalResult {
        val product = productRepository.findById(command.productId)
            ?: throw ResourceNotFoundException(...)
        if (product.isOwnedBy(command.renterId)) {  // 차단 no-if-throw-in-usecase
            throw BusinessException(...)
        }
        product.validateAvailableForRental()
        val <SERVICE_B> = <DomainSample>.create(...)
        return RequestRentalResult.of(rentalRepository.save(<SERVICE_B>))
    }
}
```

### 올바른 패턴

```kotlin
// ✅ GOOD — UseCase는 DomainService만 호출
class RequestRentalUseCase(
    private val rentalDomainService: RentalDomainService,
) {
    @Transactional
    fun execute(command: RequestRentalCommand): RequestRentalResult {
        val <SERVICE_B> = rentalDomainService.requestRental(command)
        return RequestRentalResult.of(<SERVICE_B>)
    }
}

// DomainService에서 조회 + 검증 + 실행
class RentalDomainService(
    private val rentalRepository: RentalRepository,
    private val productDomainService: ProductDomainService,
    private val eventPublisher: DomainEventPublisher,
) {
    fun requestRental(command: RequestRentalCommand): <DomainSample> {
        val product = productDomainService.getProductById(command.productId)
        product.validateNotOwnedBy(command.renterId)   // Entity 내부에서 throw
        product.validateAvailableForRental()
        val <SERVICE_B> = <DomainSample>.create(command)
        return rentalRepository.save(<SERVICE_B>).also {
            eventPublisher.publishAll(it.pullDomainEvents())
        }
    }
}

// Entity 내부 캡슐화 — Rich Domain Model
class Product(...) {
    fun validateNotOwnedBy(userId: Long) {
        if (isOwnedBy(userId)) throw SelfRentalException(...)
    }
    fun validateAvailableForRental() {
        if (!status.canRent()) throw NotRentableException(...)
    }
}
```

## Entity 규칙 (Rich Domain Model)

- 비즈니스 로직(검증/상태 전이/계산)은 Entity 메서드에 캡슐화
- Entity 내부에 `Repository / Gateway / DomainEventPublisher` 주입 금지 — Entity는 순수
- Anemic Domain Model(getter/setter만) 금지
- 상태 전이는 Enum 내부 `canTransitTo()`로 캡슐화
- Domain Event는 Entity 내부 `@Transient domainEvents` 리스트에 적재 → DomainService가 `DomainEventPublisher.publishAll()`로 발행
- 다른 도메인 데이터는 **ID(Long)만 보유** — Entity 객체 직접 참조 금지

## Audit 컬럼 + Soft Delete (모든 Entity 필수)

### 정책

모든 비즈니스 Entity (도메인 모델) 는 다음 6개 컬럼을 가진다. 예외는 audit/security 로그 같은 immutable append-only 테이블에 한정한다.

| 컬럼 | 타입 | NULL | 의미 |
|------|------|------|------|
| `created_at` | DATETIME(6) | NOT NULL | 생성 시각 (UTC). JPA `@CreatedDate` 가 자동 채움 |
| `created_by` | BIGINT | NULL 허용 | 생성자 user id. JPA `@CreatedBy` 가 `AuditorAware` 빈 (SecurityContext) 에서 자동 채움. 시스템 이벤트는 NULL |
| `updated_at` | DATETIME(6) | NOT NULL | 마지막 수정 시각. JPA `@LastModifiedDate` 가 매 save 시 갱신 |
| `updated_by` | BIGINT | NULL 허용 | 마지막 수정자 user id. JPA `@LastModifiedBy` 가 자동 채움 |
| `deleted_at` | DATETIME(6) | NULL | 소프트 삭제 시각. NULL 이면 활성. NOT NULL 이면 논리적 삭제 |
| `deleted_by` | BIGINT | NULL 허용 | 삭제자 user id. soft-delete 호출 시 같이 기록 |

### Soft Delete 규칙

- **Hard delete 금지**: `repository.delete(entity)` / `repository.deleteById(id)` / SQL `DELETE FROM` 등 직접 삭제 금지.
  - 예외: 법무 요구(GDPR right-to-erasure), 회계 마감 후 audit-trail 청산 같은 명시 사유 — admin 전용 UseCase 에서만 허용.
- **Soft delete 진입점**: Entity 의 `softDelete(userId: Long?)` 메서드 한 곳만.
- **기본 조회 필터**: Repository 의 `findById` / `findByX` 는 기본으로 `deletedAt IS NULL` 필터. 삭제 포함 조회는 별도 `findAllIncludingDeleted` 같은 명시 메서드.
- **상태 전이**: 이미 삭제된 entity 에 비즈니스 동작 호출 시 `IllegalStateException` 또는 도메인 예외.

### JpaAuditingBase (JPA, @MappedSuperclass)

> 베이스 클래스 이름이 `*Entity*` 패턴이 아닌 `JpaAuditingBase` 인 이유: `no-default-constructor-values` 룰(`*Entity*.kt` glob)이 `lateinit var` 와 audit 인프라 placeholder 와 충돌하기 때문. 도메인 Entity 는 **`Entity` 접미사 없이** 도메인명 그대로 작성하고 베이스를 상속한다 — `class User : JpaAuditingBase()`, `class Product : JpaAuditingBase()` (네이밍 컨벤션 테이블과 일치).

```kotlin
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class JpaAuditingBase {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: ZonedDateTime
        protected set

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    var createdBy: Long? = null
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: ZonedDateTime
        protected set

    @LastModifiedBy
    @Column(name = "updated_by")
    var updatedBy: Long? = null
        protected set

    @Column(name = "deleted_at")
    var deletedAt: ZonedDateTime? = null
        protected set

    @Column(name = "deleted_by")
    var deletedBy: Long? = null
        protected set

    fun softDelete(userId: Long?) {
        check(deletedAt == null) { "Entity is already soft-deleted" }
        deletedAt = ZonedDateTime.now()
        deletedBy = userId
    }

    val isDeleted: Boolean get() = deletedAt != null
}
```

**활성화 조건**:
- `@SpringBootApplication` 클래스에 `@EnableJpaAuditing` 명시
- `AuditorAware<Long>` 빈 — SecurityContext 에서 user id 추출, 미인증 요청은 `Optional.empty()` 반환
- `BaseEntity` 는 `domain.common` 패키지 — 도메인 layer 가 JPA 어노테이션을 import 하는 유일한 예외

### BaseDocument (MongoDB)

```kotlin
// 활성화: @SpringBootApplication 클래스에 @EnableMongoAuditing 명시 (추상 베이스에 붙이지 않는다)
abstract class BaseDocument {
    @CreatedDate
    lateinit var createdAt: ZonedDateTime
        protected set

    @CreatedBy
    var createdBy: Long? = null
        protected set

    @LastModifiedDate
    lateinit var updatedAt: ZonedDateTime
        protected set

    @LastModifiedBy
    var updatedBy: Long? = null
        protected set

    var deletedAt: ZonedDateTime? = null
        protected set

    var deletedBy: Long? = null
        protected set

    fun softDelete(userId: Long?) {
        check(deletedAt == null) { "Document is already soft-deleted" }
        deletedAt = ZonedDateTime.now()
        deletedBy = userId
    }

    val isDeleted: Boolean get() = deletedAt != null
}
```

### Migration SQL 패턴

모든 신규 테이블 CREATE 문에 audit 6 컬럼 포함 (Flyway):

```sql
CREATE TABLE bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- 도메인 컬럼...
    facility_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    -- audit + soft delete
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    INDEX idx_bookings_deleted_at (deleted_at),
    INDEX idx_bookings_status_deleted_at (status, deleted_at)
);
```

**필수 인덱스**: `deleted_at` 단독 + `status` (또는 자주 조회되는 도메인 컬럼) + `deleted_at` 복합. 모든 조회가 `WHERE deleted_at IS NULL` 필터를 거치므로 인덱스 없으면 풀스캔.

## 레이어 의존 방향

```
presentation → application → domain ← infrastructure
```

- Domain은 **JPA/Mongo 매핑 어노테이션(`jakarta.persistence.*`, `org.springframework.data.annotation.*`, `org.hibernate.annotations.*`) 외에는** import 하지 않는다. 이 외 외부 라이브러리·infrastructure import 금지
- Infrastructure는 Domain의 Repository / Gateway / DomainEventPublisher interface를 구현 (JpaRepository 는 도메인 Entity 를 직접 다룬다 — 별도 영속화 POJO 없음)
- 도메인 패키지 간 참조 금지 (`domain.<SERVICE_B>`에서 `domain.product` import 불가, `domain.common`만 허용)
- Entity 의 *동작*은 여전히 순수: Repository/Gateway/EventPublisher 를 Entity 필드로 주입 금지 (필요 시 메서드 인자로 전달)

## 네이밍 컨벤션

| 역할 | 위치 | 파일명 |
|---|---|---|
| Controller | presentation | `~ApiController.kt` |
| Kafka Consumer / EventListener | presentation | `~EventWorker.kt` |
| UseCase | application | `~UseCase.kt` |
| Entity (= JPA `@Entity`, 단일 모델) | domain | 도메인명 (`User.kt`, `Product.kt`) — 별도 `~Entity.kt` POJO 금지 |
| Domain Repository (DB 영속화) | domain | `~Repository.kt` (interface) |
| Domain Gateway (외부 시스템 호출: 외부 API/SMS/이메일) | domain | `~Gateway.kt` (interface) |
| Domain Event Publisher | domain | `DomainEventPublisher.kt` (interface) |
| Spring Data JPA Repository | infrastructure | `~JpaRepository.kt` — `JpaRepository<도메인Entity, Long>` 직접 |
| Repository 구현 (Domain interface → JpaRepository 위임 + QueryDSL) | infrastructure | `~RepositoryImpl.kt` |
| Gateway 구현 | infrastructure | `~GatewayImpl.kt` |
| Event Publisher 구현 | infrastructure | `KafkaDomainEventPublisher.kt` 등 |
| Command | application | `~Command.kt` |
| Request | presentation | `~Request.kt` |
| Response | application | `~Response.kt` |

**Repository vs Gateway 구분**:
- Repository → DB 영속화 (JPA, MyBatis, MongoDB 등)
- Gateway → 외부 시스템 호출 (외부 SaaS API, SMS, 이메일, 결제, 푸시 등)

### PK 네이밍
- 엔티티 PK는 `id`로 통일 (`user_id` X, `id` O)
- 참조 FK 컬럼은 `user_id`, `product_id` 사용

### 변수명
- 풀네임 강제 — `workspaceId` ✓, `ws` ✗
- 약어 금지: `comp` → `component`, `eval` → `evaluation`

## DTO 흐름

```
Request (presentation)
  → Command (application)
    → Entity (domain)
      → Response (application)
        → 그대로 presentation 반환
```

- Request: 외부 입력 형태
- Command: UseCase 실행 파라미터 (`toCommand()`로 변환)
- Response: UseCase 반환값, presentation이 그대로 사용
- **Entity ↔ 영속화 POJO 변환 단계 없음** — JPA Entity 가 도메인 Entity 이므로 toEntity()/toDomain() 매퍼 금지. Command → Entity 직접 생성, Entity → Response 직접 매핑.

## 트랜잭션 & 이벤트

| 상황 | 위치 |
|---|---|
| 기본 트랜잭션 | UseCase `@Transactional` |
| Domain Event 발행 | DomainService가 `DomainEventPublisher` interface(domain layer)로 호출, 구현체는 infrastructure (Kafka·Spring ApplicationEventPublisher 등) |
| 이벤트 처리 | `@TransactionalEventListener(AFTER_COMMIT)` — **presentation layer**에 위치, UseCase 경유 |
| 도메인 간 이벤트 | 비동기 (`@Async` + `@Retryable`) |

## 클린 코드 규칙

- UseCase `execute()` 10줄 이내
- DomainService 메서드 15줄 이내
- 한 메서드는 하나의 추상화 수준만 (`entity.markPaid()`와 `repository.save()` 공존 금지)
- Guard clause(early return) 적극 사용
- if-else 중첩 depth 2 이상 금지
- 매직 넘버/문자열 금지 (상수/enum)
- 주석 대신 메서드명으로 의도 표현
- 한 메서드는 한 가지 일만

### Guard Clause 예시

```kotlin
// ❌ BAD
fun process(id: Long): Result {
    val entity = repository.find(id)
    if (entity != null) {
        if (entity.isValid()) {
            return entity.process()
        } else {
            throw InvalidException()
        }
    } else {
        throw NotFoundException()
    }
}

// ✅ GOOD
fun process(id: Long): Result {
    val entity = repository.find(id) ?: throw NotFoundException()
    if (!entity.isValid()) throw InvalidException()
    return entity.process()
}
```

## Null 안전

- `!!` 절대 금지 (하네스 차단)
- 대체: `requireNotNull`, `?:`, `?.let`
- nullable이 필요 없으면 `non-null`로 선언
- **`Optional` 사용 금지** — Kotlin nullable (`T?`) 로 표현. `Optional` 은 Java 호환 API 경계(예: Spring Data `AuditorAware<Long>`) 가 강제하는 경우만 허용한다.
  - ❌ `fun findByEmail(email: String): Optional<User>`
  - ✅ `fun findByEmail(email: String): User?`
  - ❌ `Optional.empty()` / `Optional.of(x)` / `optional.orElseThrow()`
  - ✅ `null` / `x` / `value ?: throw ...`
  - 예외: `AuditorAware<Long>` 는 Spring API 시그니처 — `Optional<Long>` 반환 필수

## 생성자/함수 호출 포맷

| 조건 | 스타일 |
|---|---|
| 파라미터 5개 이하 + 타입 전부 다름 | namedArgument 없이 한 줄 |
| 파라미터 5개 이하 + 타입 중복 | namedArgument + 한 줄씩 개행 |
| 파라미터 5개 초과 | namedArgument + 한 줄씩 개행 |

## QueryDSL

- `@Query` 금지 (하네스 차단)
- CustomRepository interface + RepositoryImpl (QueryDSL) 패턴
- JpaRepository는 기본 CRUD만, 복잡 쿼리는 CustomRepositoryImpl에

## JSON 컬럼

- `@Type(JsonStringType::class)` + data class
- `ObjectMapper` 직접 사용 금지

## Kafka Consumer

- **위치**: presentation layer (`~EventWorker.kt`) — Controller와 동일한 외부 진입점
- `ConsumerRecord<String, String>` 금지 → DTO 직접 매핑
- `JsonDeserializer` + `trusted.packages` 설정
- Consumer에서 Repository / DomainService 직접 호출 금지 → **UseCase 경유**
- ObjectMapper 수동 파싱 금지

```kotlin
// presentation/consumer/PlanChangedEventWorker.kt
@Component
class PlanChangedEventWorker(
    private val planChangedUseCase: PlanChangedUseCase,
) {
    @KafkaListener(topics = ["plan.changed.v1"])
    fun consume(event: PlanChangedEvent) {
        planChangedUseCase.execute(event.toCommand())
    }
}
```

## 필수 테스트 레이어

모두 존재해야 PR 승인 가능. TDD(Test-Driven Development) 사이클(RED → GREEN → detekt)을 따른다.

| 레이어 | 대상 | 타입 | 도구 |
|---|---|---|---|
| domain | Entity, DomainService | 단위 | Kotest BehaviorSpec + MockK |
| application | UseCase | 단위 | Kotest + MockK (DomainService 모킹) |
| infrastructure | Repository·Gateway·DomainEventPublisher 구현 | 통합 | Kotest + TestContainers (MySQL/Redis/Kafka) |
| presentation | Controller, **EventWorker(Kafka Consumer), EventListener** | 통합 | Kotest + MockMvc/WebTestClient + TestContainers |
| scenario | E2E 비즈니스 플로우 | 시나리오 통합 | 가입→등록→대여→반납 등 전체 시나리오 |

## 티켓 사이즈

- S: ~200줄 (구현 코드 기준, 테스트 제외)
- M: ~400줄
- L: ~800줄 (초과 시 분할)

## 패키지 통합·이전 시 적용 원칙

패키지 리네임/통합은 **단순 디렉토리 이동이 아닌 아키텍처 정리 기회**다.

### 금지

- **typealias 호환 layer 신규 생성 금지** — `*TypeAliases.kt`, `*Compat.kt`, `*Aliases.kt` 파일을 새로 만들지 말 것. 호출부 import를 같은 티켓 범위에서 100% 갱신한다. "다음 wave에서 제거" 약속 패턴은 영원히 남는 잔재가 되므로 금지.
- **단순 디렉토리 이동만 하는 티켓 금지** — `git mv` + package 선언 변경만 하고 끝나면 안 된다.

### 필수

패키지 이전 티켓은 다음을 함께 수행한다:

1. **Port 인터페이스 발견 시 제거** — `interface *Port` / `class *Adapter` 패턴이 있으면 Repository/Gateway 직접 주입으로 전환.
2. **Adapter 패턴 잔재 → DomainService로 책임 흡수** — 단순 위임 Adapter는 제거.
3. **Anemic Domain Model → Rich Domain Model 재배치** — getter/setter만 있는 Entity는 비즈니스 메서드를 Entity 내부로 이동.
4. **Hexagonal layer 책임 재배치** — `domain` / `application` / `infrastructure` 책임을 명확히.
5. **호출부 import 100% 갱신** — 구 패키지 디렉토리는 티켓 완료 시점에 0 파일이어야 한다.

### 검증

```bash
# 구 패키지 디렉토리 잔재 0건
find <module>/src -path '*<old-package-path>*' -name '*.kt' | wc -l   # → 0

# Port 인터페이스 0건
grep -rn "interface .*Port" <module>/src/main --include="*.kt"        # → 0건

# typealias 호환 layer 0건
find <module>/src -name "*TypeAliases.kt" -o -name "*Compat.kt" -o -name "*Aliases.kt" | wc -l   # → 0
```

## 참고 문서
- [output-style](./output-style.md) — 문체/코드 참조 형식
- [tdd-template](./tdd-template.md) — 기술 설계 문서 템플릿
- [ticket-guide](./ticket-guide.md) — 티켓 작성 규약