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
| JPA 기능 | `@Entity`, `@Table`, `@Column`, `@Embedded`, **`@OneToMany`/`@ManyToOne`/`@OneToOne`(같은 aggregate 내부 연관관계)**, `@Version`(낙관락), `@Convert`, lifecycle callback 등 사용 허용. 다대다는 매핑 Entity 로 풀어쓰기 권장(아래 참조) |
| domain import 예외 | `jakarta.persistence.*`, `org.springframework.data.annotation.*`(@CreatedDate 등), `org.hibernate.annotations.*`(필요 시) **만** 허용. `infrastructure.*` / 다른 `domain.<other>` import 는 여전히 금지 |
| 영속화 분리 클래스 | `infrastructure/persistence/<context>/<X>Entity.kt` 같은 **별도 POJO 영속화 모델 생성 금지**. 매퍼(toEntity/toDomain) 도 만들지 않는다 |
| Repository 반환 타입 | 도메인 Entity 직접 반환. `JpaRepository<UserEntity, Long>` 가 아니라 `JpaRepository<User, Long>` |
| Aggregate 경계 | **같은 aggregate 내부**는 JPA 연관관계(`@OneToMany`/`@ManyToOne`/`@OneToOne`/`@Embedded`)로 직접 연결한다. **다른 aggregate** 데이터는 연관 객체 대신 **FK id(Long)** 로 맺는다 (`@ManyToOne OtherRoot` ❌ → `val otherId: Long` ✅) — aggregate 경계를 넘는 객체 그래프 로딩 방지 |
| 다대다 | `@ManyToMany`/`@JoinTable` 대신 **매핑 테이블을 독립 Entity 로** 푸는 것을 권장(부여 시각·부여자·audit 컬럼을 담기 위함, 아래 참조). 부가 속성이 없는 단순 다대다는 association 직접 사용도 허용 |
| audit/soft-delete | `JpaAuditingBase` 상속 (`class User : JpaAuditingBase()`), MongoDB 는 `BaseDocument` |
| 순수성 트레이드오프 | "domain 은 어떤 것도 import 안 한다" 원칙을 **JPA 매핑 어노테이션에 한해 완화**한다. ORM 매핑은 도메인 영속성의 일부로 간주. 단 *동작*은 여전히 순수 — Repository/Gateway 를 Entity 에 주입하지 않는다 |

**왜 이 결정인가**: POJO 도메인 ↔ JPA Entity 이중 모델은 매퍼 보일러플레이트, 동기화 버그, lazy-loading 경계 혼란을 유발한다. 단일 모델로 Rich Domain + JPA 를 동시에 취한다. 트레이드오프(도메인의 ORM 결합)는 수용한다.

### 다대다 — 매핑 Entity 로 풀어쓰기 (권장)

다대다에 부여 시각·부여자·만료/활성 같은 부가 속성이 필요하면 `@ManyToMany` / `@JoinTable` 대신 **매핑 테이블을 1급 Entity 로 노출**한다. (부가 속성이 전혀 없는 순수 다대다는 association 직접 사용도 허용.)

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

## 패키지 구조 (Package Layout)

루트 패키지는 `com.sportsapp.<layer>.<context>.<sub>` 형태다. layer 가 1차, **도메인 컨텍스트가 2차, 역할(sub)이 3차**다. infrastructure 도 동일하게 **도메인 우선**으로 둔다 (기술 우선 `persistence/<context>` 금지).

> 한 레이어/도메인 디렉토리에 모든 파일을 평면(flat)으로 두지 않는다. 아래 sub-package 로 반드시 나눈다.

### presentation/<context>/

| sub-package | 책임 | 파일명 |
|---|---|---|
| `controller/` | REST 진입점 | `~ApiController.kt` |
| `worker/` | Kafka Consumer + 내부 이벤트 리스너 (비동기/외부 이벤트 진입점) | `~EventWorker.kt` |
| `scheduler/` | `@Scheduled` 주기 작업 | `~Scheduler.kt` |
| `batch/` | Spring Batch Job/Step 설정 | `~Batch.kt` / `~BatchConfig.kt` |
| `dto/request/` | 외부 요청 바디 | `~Request.kt` |
| `dto/response/` | 외부 응답 바디 | `~Response.kt` |

- `@TransactionalEventListener(AFTER_COMMIT)` 도 `worker/` 에 둔다 (이벤트 진입점).
- `batch/`·`scheduler/` 는 해당 작업이 없으면 디렉토리를 만들지 않는다 (빈 패키지 금지).

### application/<context>/

| sub-package | 책임 | 파일명 |
|---|---|---|
| `usecase/` | 행위 1개 = 클래스 1개 | `~UseCase.kt` |
| `dto/` | UseCase 입력 `Command` + 내부 반환 `Result` | `~Command.kt`, `~Result.kt` |

### domain/<context>/

| sub-package | 책임 | 파일명 |
|---|---|---|
| `service/` | DomainService (조회·연결·persist 오케스트레이션) | `~DomainService.kt` |
| `entity/` | `@Entity` aggregate root + 자식 entity + **aggregate 상태 enum(Status)** | `Booking.kt`, `BookingStatus.kt` |
| `vo/` | `@Embeddable` 값 객체 + aggregate 에 귀속되지 않는 순수 도메인 enum | `Money.kt`, `SeatGrade.kt` |
| `dto/` | 쿼리 projection / 도메인 조회 결과 객체 | `FacilityKpiSummary.kt` |
| `repository/` | Repository interface | `~Repository.kt` |
| `gateway/` | Gateway interface | `~Gateway.kt` |
| `event/` | DomainEvent | `~Event.kt` |
| `exception/` | 도메인 예외 | `~Exception.kt` |

- **Status enum 위치**: aggregate 의 상태를 표현하고 `canTransitTo()` 같은 행위를 가지면 `entity/` 에 aggregate 와 같이 둔다. aggregate 에 귀속되지 않는 순수 분류 enum 은 `vo/`.
- `domain/common/` 은 도메인 공통 산출물: `DomainEventPublisher` interface, `BusinessException` base, `JpaAuditingBase`/`BaseDocument`, 공통 exception/security/storage. 다른 도메인은 `domain.common` 만 import 허용 (도메인 간 직접 참조 금지 원칙 유지).

### infrastructure/<context>/ (도메인 우선)

| sub-package | 책임 | 파일명 |
|---|---|---|
| `mysql/` | Spring Data JpaRepository + Custom interface + RepositoryImpl(QueryDSL) | `~JpaRepository.kt`, `~RepositoryImpl.kt` |
| `mongo/` | MongoDB Repository/Template 구현 | `~MongoRepositoryImpl.kt` |
| `kafka/` | `DomainEventPublisher` 구현(Producer), Avro/직렬화 어댑터 | `Kafka~Publisher.kt` |
| `gateway/` | Gateway 구현(외부 API client) | `~GatewayImpl.kt` |

- **도메인 비귀속(cross-cutting) 인프라**는 도메인 디렉토리 밖에 둔다: `infrastructure/{config, security, messaging, redis, storage, lock, audit, external, persistence}`. 예) `messaging/` 의 RoutingPublisher, `external/` 의 RestClientFactory, `config/` 의 CacheConfig.
- `infrastructure/persistence/` 는 **전역 영속화 지원만** 허용한다 — `ZonedDateTime` JPA `@Converter`, Mongo 타입 컨버터, `MongoAuditorAware` 같이 특정 도메인에 귀속되지 않는 cross-cutting 컨버터/auditor. **도메인 Repository 구현(`~RepositoryImpl`)은 절대 여기 두지 않는다** — 그것은 `infrastructure/<context>/{mysql,mongo}` 다.
- 한 도메인이 mysql·mongo·kafka·gateway 중 일부만 쓰면 쓰는 것만 만든다.

### 디렉토리 예시 (booking)

```
presentation/booking/
  controller/ BookingApiController.kt  SlotApiController.kt
  worker/     BookingRequestedEventWorker.kt
  dto/request/  CreateBookingRequest.kt  CancelBookingRequest.kt  CreateSlotRequest.kt
  dto/response/ BookingResponse.kt  SlotResponse.kt  ListBookingsResponse.kt
application/booking/
  usecase/ CreateBookingUseCase.kt  CancelBookingUseCase.kt  ...
  dto/     CreateBookingCommand.kt  CreateBookingResult.kt  ...
domain/booking/
  service/    BookingDomainService.kt  SlotDomainService.kt
  entity/     Booking.kt  Slot.kt  BookingStatus.kt
  dto/        FacilityKpiSummary.kt
  repository/ BookingRepository.kt  SlotRepository.kt  BookingKpiQueryRepository.kt
  gateway/    PaymentRefundGateway.kt
  event/      BookingRequestedEvent.kt  BookingCancelledEvent.kt
  exception/  InvalidSlotException.kt  SlotFullException.kt  RefundPolicyViolationException.kt ...
infrastructure/booking/
  mysql/ BookingJpaRepository.kt  BookingQueryDslRepository.kt  BookingRepositoryImpl.kt  SlotRepositoryImpl.kt  BookingKpiQueryRepositoryImpl.kt
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
- **다른 aggregate** 데이터는 **ID(Long)만 보유** — 같은 aggregate 내부 자식은 JPA 연관관계(`@OneToMany`/`@ManyToOne`/`@OneToOne`)로 직접 참조

### Self-Validation 캡슐화 (DomainService에 검증 메서드 금지)

**Entity의 자기 상태로 답할 수 있는 검증은 Entity의 public 메서드여야 한다.** DomainService에 `private fun validateXxx(entity)` / `private fun requireXxxPositive(amount)` 같은 helper를 두는 것은 **Anemic 도메인의 변종**이다. DomainService가 Entity의 내부 상태를 알고 직접 검사하면, 같은 검증 규칙이 다른 UseCase에서 호출될 때 다시 복사된다.

| 패턴 | 위치 | 예시 |
|---|---|---|
| 인자값 형식·범위 검증 (음수/0/빈 문자열) | **Entity 팩토리 또는 도메인 동작 메서드 내부** `require(amount > 0)` | `CartItem.create(quantity)` 가 직접 require / `deduct(amount)` 가 `if (amount <= 0) throw InvalidQuantityException(amount)` |
| 자기 상태 검증 (`status != ACTIVE`, `isDeleted`, `isLocked`) | **Entity 메서드** `product.requireActive()` / `product.validateAvailableForRental()` | `if (status != ACTIVE) throw ProductInactiveException(id)` 가 Product 안에 |
| 자기 데이터 비교 (`stock.quantity < required`) | **Entity 메서드** `stock.requireSufficient(required)` | `if (quantity < required) throw OutOfStockException(productId, required, quantity)` 가 Stock 안에 |
| 다른 Aggregate 데이터 비교 (`booking.userId != requesterId`) | **Entity 메서드** `booking.requireOwnedBy(userId)` | "본인 확인" 같이 Entity가 자기 필드와 인자만 비교 |

### 안티 패턴 (DomainService에서 캡슐화 가능한 검증 helper)

```kotlin
// ❌ BAD — DomainService 가 private helper 로 Entity 상태를 직접 검사
class CartDomainService(...) {
    fun addItem(cartId: Long, productId: Long, quantity: Int) {
        requirePositiveQuantity(quantity)             // ← Entity 책임
        validateProductActive(productId)              // ← Entity 책임
        validateStockSufficient(productId, quantity)  // ← Entity 책임
        // ...
    }

    private fun requirePositiveQuantity(quantity: Int) {
        if (quantity <= 0) throw InvalidQuantityException(quantity)
    }
    private fun validateProductActive(productId: Long) {
        val product = productRepository.findById(productId) ?: throw ResourceNotFoundException("Product", productId)
        if (product.status != ProductStatus.ACTIVE) throw ProductInactiveException(productId)
    }
    private fun validateStockSufficient(productId: Long, required: Int) {
        val stock = stockRepository.findByProductId(productId) ?: throw ResourceNotFoundException("Stock", productId)
        if (stock.quantity < required) throw OutOfStockException(productId, required, stock.quantity)
    }
}
```

### 올바른 패턴 (Entity 자체가 검증)

```kotlin
// ✅ GOOD — Entity 가 자기 상태로 답한다
class Product(...) {
    fun requireActive() {
        if (status != ProductStatus.ACTIVE) throw ProductInactiveException(id)
    }
}

class Stock(...) {
    fun requireSufficient(required: Int) {
        if (quantity < required) throw OutOfStockException(productId, required, quantity)
    }
}

class CartItem(...) {
    companion object {
        fun create(cartId: Long, productId: Long, quantity: Int): CartItem {
            require(quantity > 0) { "quantity must be positive" }  // 또는 InvalidQuantityException
            return CartItem(...)
        }
    }
    fun addQuantity(amount: Int) {
        require(amount > 0) { "amount must be positive" }
        quantity += amount
    }
}

// DomainService 는 조회·연결·persist 만
class CartDomainService(...) {
    fun addItem(cartId: Long, productId: Long, quantity: Int) {
        val product = productDomainService.getById(productId)
        product.requireActive()                                  // Entity 가 답
        val stock = stockDomainService.getByProductId(productId)
        stock.requireSufficient(quantity)                        // Entity 가 답
        val item = CartItem.create(cartId, productId, quantity)  // 팩토리 내부 require
        cartItemRepository.save(item)
    }
}
```

### 판단 기준

**"이 검증을 위해 필요한 데이터를 모두 Entity 자기 자신이 들고 있는가?"**

- ✅ Yes → Entity 메서드 (`requireXxx`, `validateXxx`, `canXxx`)
- ❌ No (Repository 조회·외부 Gateway 호출 필요) → DomainService

`requirePositiveQuantity(quantity: Int)` 같이 **인자값 단독 검증**은 Entity 팩토리 또는 동작 메서드의 `require` 한 줄로 흡수. DomainService에 별도 helper 두지 않는다.

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

### Aggregate 생명주기 — 루트 soft-delete 시 자식 전파 필수 (고아 금지)

같은 aggregate 내부 자식은 JPA 연관관계(`@OneToMany` 등)로 연결하고 **`cascade = [PERSIST, MERGE]`** 로 저장을 전파한다. 단 이 코드베이스는 **soft-delete** 를 쓰므로 **`orphanRemoval` / `CascadeType.REMOVE`(= hard delete) 는 쓰지 않는다.** 따라서 루트를 soft-delete(또는 취소·종료) 할 때 자식의 `softDelete()` 도 **같은 트랜잭션 안에서 명시적으로 전파**해야 한다. cascade 가 soft-delete 를 대신해 주지 않으므로, 이를 빠뜨리면 루트가 soft-delete 돼도 자식이 `deleted_at IS NULL` 로 살아남는 **고아(orphan)** 가 된다. (aggregate **간** 참조는 `no-db-fk` 와 일관되게 FK id(Long).)

**규칙**: aggregate 루트를 soft-delete(또는 취소·종료 등 생명주기 종료) 하는 DomainService 메서드는 **같은 트랜잭션 안에서 자식도 전부 soft-delete(또는 동등 상태 전이) 해야 한다.** 루트만 지우고 끝내면 안 된다.

| aggregate 루트 → 자식 | 루트 종료 시 반드시 |
|---|---|
| Post → Comment | `commentRepository.softDeleteAllByPostId(postId, userId)` 동반 |
| TicketOrder → Ticket | 주문 취소 시 발권된 Ticket 도 취소/soft-delete (좌석 unique 점유 해제) |
| Event → Seat | 이벤트 삭제 시 `seatRepository.softDeleteByEventId(eventId, userId)` 동반 |
| Order → OrderItem | 주문 취소/삭제 시 item 도 동반 처리 |

- **자식 전파 메서드를 정의만 하고 호출하지 않는 것 금지** — `softDeleteByXxxId` 같은 메서드를 만들었으면 루트 종료 경로에서 반드시 호출. 데드 메서드는 고아의 신호다.
- **검증**: aggregate 마다 "루트 soft-delete → 자식 조회 0건" 시나리오/레포지토리 테스트를 필수로 작성한다 (ticket-guide 테스트 체크리스트 참조).
- **트랜잭션 한 단위**: 루트+자식 생명주기 변경은 한 `@Transactional`(UseCase) 안에서 원자적으로. 부모만 저장되고 자식이 누락된 반쪽 aggregate 가 생기면 안 된다.

> 판단 기준: "이 Entity 는 루트가 사라지면 **독립적으로 존재할 의미가 있는가?**" 없으면 같은 aggregate 의 자식이며, 루트 생명주기에 종속돼야 한다.

### RepositoryImpl 은 순수 영속화만 — 비즈니스 로직 금지

`infrastructure/persistence/**/*RepositoryImpl.kt` 는 **JpaRepository 위임 + QueryDSL 조회**만 한다. 다음은 **DomainService 의 책임**이며 RepositoryImpl 에 두면 레이어 위반이다.

- 중복 데이터 해소 (중복 row 탐지 후 골라 살리고 나머지 soft-delete)
- `softDelete()` 호출 / soft-delete 여부 판단 / 상태 결정 분기
- 활성 마커(`activeMarker`) 같은 도메인 불변식 관리
- 조건부 재저장, 보정 로직

```kotlin
// ❌ BAD — RepositoryImpl 이 중복 해소·softDelete·activeMarker 를 결정 (비즈니스 로직이 infra 에)
override fun findByUserId(userId: Long): Cart? {
    val activeCarts = cartJpaRepository.findAllByUserIdAndDeletedAtIsNull(userId)
    if (activeCarts.size <= 1) return activeCarts.firstOrNull()
    val newest = activeCarts.maxBy { it.id }                  // ← 도메인 결정
    val duplicates = activeCarts.filter { it.id != newest.id }
    duplicates.forEach { it.softDelete(userId = null); it.activeMarker = null }  // ← 도메인 동작
    cartJpaRepository.saveAll(duplicates)
    return newest
}

// ✅ GOOD — RepositoryImpl 은 조회만, 중복 해소는 DomainService 가
override fun findActiveByUserId(userId: Long): List<Cart> =
    cartJpaRepository.findAllByUserIdAndDeletedAtIsNull(userId)

// DomainService 에서
fun getOrResolveCart(userId: Long): Cart {
    val carts = cartRepository.findActiveByUserId(userId)
    return cartDomainService.resolveDuplicates(carts, userId)   // 중복 해소·softDelete 는 도메인 책임
}
```

- **판단 기준**: RepositoryImpl 메서드가 `if/when` 으로 분기하거나 `softDelete()`/엔티티 상태 변경을 호출하면 거의 항상 위반이다. RepositoryImpl 은 "넣고/꺼내는" 것만 한다.
- 멱등 보정(중복 정리)이 정말 필요하면 명시적 DomainService 메서드(`resolveDuplicateCarts`)로 끌어내고, RepositoryImpl 은 그 입력이 될 raw 조회만 제공한다.

### UseCase 에서 외부/결제 상태 동기 분기 금지

UseCase 가 `paymentDomainService.create()` 직후 `when (payment.status)` / `if (payment.status == ...)` 로 confirm/cancel 을 동기 분기하면 안 된다. 결제 준비(`prepare`)는 항상 미완(READY)이므로 동기 분기는 정상 흐름을 깨뜨린다. 결제 완료는 **웹훅/이벤트(비동기)** 로 받아 주문을 확정한다 (`payment.completed.v1` → 각 주문 컨텍스트가 자기 EventWorker 에서 확정, AFTER_COMMIT 발행).

### 동시성·멱등성 최종 방어선

- **capacity/예약/좌석/재고류 테이블**: 분산락·낙관락은 보조 수단이고, **DB 제약(부분 unique 인덱스 등)을 최종 방어선**으로 둔다. (`no-db-fk` 는 외래키만 금지 — unique 제약은 허용·권장.) 락 TTL 만료·STW 로 락이 풀려도 DB 가 오버부킹/이중발권을 막아야 한다.
- **낙관락 충돌**: `@Version` 을 쓰면 `ObjectOptimisticLockingFailureException` 을 `@Retryable` 또는 도메인 예외(409 품절 등)로 처리한다. 그대로 500 으로 노출 금지.
- **트랜잭션 내 외부 호출 금지**: PG·SMS·푸시 등 외부 Gateway 호출을 `@Transactional` 안에서 하지 않는다 (DB 커넥션 장기 점유 + 보상 불일치).
- **도메인 이벤트 발행**: 커밋 전 발행 금지. `@TransactionalEventListener(AFTER_COMMIT)` 경로로 통일 (롤백 시 유령 이벤트 방지).

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

## 패키지 구조 (DDD Bounded Context + OOP)

### 배치 규칙: layer-first → context → aggregate

최상위는 **레이어**, 그 아래가 **bounded context**, 그 안에 **aggregate** 단위로 배치한다. 4개 레이어에서 context 디렉토리명은 **동일**해야 한다 (`presentation/booking` ↔ `application/booking` ↔ `domain/booking` ↔ `infrastructure/booking`).

```
com.sportsapp
├── presentation/<context>/         ~ApiController, ~EventWorker, ~Request
├── application/<context>/          ~UseCase, ~Command, ~Response
├── domain/
│   ├── <context>/                  Aggregate(@Entity), 자식 Entity, VO, DomainService,
│   │                               ~Repository·~Gateway·DomainEventPublisher (interface)
│   └── common/                     JpaAuditingBase·공통 VO — 유일하게 context 횡단 import 허용
└── infrastructure/
    ├── <context>/                  ~RepositoryImpl, ~GatewayImpl, ~JpaRepository
    │   └── (QueryDSL CustomImpl)
    └── persistence/<context>/      context 별 QueryDSL Custom 구현 (선택)
```

- **Bounded Context** = 최상위 도메인 경계 (booking·payment·ticketing·facility·goods·post·user·notification·weather·operator·dashboard 등). 한 context = 한 책임 영역.
- **Aggregate** = context 안의 일관성·트랜잭션 경계. root Entity + 자식 Entity + VO 를 같은 context 패키지에 둔다.
- 새 도메인은 **새 context 패키지를 4개 레이어에 동시 신설**한다 (기존 파일 교집합 ∅ → 같은 wave 병렬 작업 안전, `ticket-guide.md` "Single Writer per File" 참조).

### DDD 규칙

| 규칙 | 내용 |
|---|---|
| context 간 import 금지 | `domain.booking` 이 `domain.payment` 를 import 금지 (정적 룰로 캡처 불가 — **pr-reviewer/code-reviewer 가 강제**). context 협력은 **FK id(Long) 보유 + 도메인 이벤트**로만 |
| common 만 횡단 공유 | `domain.common` (JpaAuditingBase·공통 VO) 만 모든 context 가 import 가능. common 에 특정 도메인 지식 누수 금지 |
| aggregate = 트랜잭션 경계 | 한 UseCase `@Transactional` 은 **한 aggregate root** 를 수정. 다른 context 변경은 이벤트(AFTER_COMMIT) 로 비동기 전파 |
| context 간 동기 조회 | 꼭 필요하면 `~Gateway` interface(domain) 로 추상화. 다른 context 의 Repository 를 직접 호출 금지 |
| aggregate 자식 생명주기 | root soft-delete/취소 시 자식도 같은 트랜잭션에서 전파 (위 "Aggregate 생명주기" 섹션) |

### OOP 규칙

| 규칙 | 내용 |
|---|---|
| Tell, Don't Ask | 상태를 꺼내 판단하지 말고 행위를 요청 — `if (order.status == PAID)` ❌ → `order.requirePaid()` ✅ (위 "Self-Validation 캡슐화"와 일관) |
| 캡슐화 | Entity 필드는 `private set`/`protected set`. 상태 변경은 의미 있는 도메인 메서드로만. getter/setter 나열(Anemic) 금지 |
| 다형성으로 분기 제거 | 상태별 분기는 enum 의 `canTransitTo()` 또는 sealed class 로 캡슐화. `when` 나열 최소화 |
| 단일 책임 | 한 클래스 = 한 책임. UseCase 1개 = 행위 1개 (위 "UseCase 규칙") |

> 판단 기준: "이 클래스/패키지를 다른 context 가 알아야 하는가?" — Yes 면 경계 설계가 틀렸다. context 는 이벤트와 FK id 로만 느슨하게 연결한다.

## 네이밍 컨벤션

| 역할 | 위치 (패키지) | 파일명 |
|---|---|---|
| Controller | `presentation/<context>/controller` | `~ApiController.kt` |
| Kafka Consumer / EventListener | `presentation/<context>/worker` | `~EventWorker.kt` |
| Scheduler | `presentation/<context>/scheduler` | `~Scheduler.kt` |
| Batch | `presentation/<context>/batch` | `~Batch.kt` |
| Request | `presentation/<context>/dto/request` | `~Request.kt` |
| Response | `presentation/<context>/dto/response` | `~Response.kt` |
| UseCase | `application/<context>/usecase` | `~UseCase.kt` |
| Command / Result | `application/<context>/dto` | `~Command.kt`, `~Result.kt` |
| DomainService | `domain/<context>/service` | `~DomainService.kt` |
| Entity (= JPA `@Entity`, 단일 모델) + Status enum | `domain/<context>/entity` | 도메인명 (`User.kt`, `Booking.kt`) — 별도 `~Entity.kt` POJO 금지 |
| Value Object / 순수 enum | `domain/<context>/vo` | `Money.kt`, `SeatGrade.kt` |
| 쿼리 projection / 조회 결과 | `domain/<context>/dto` | `FacilityKpiSummary.kt` |
| Domain Repository (interface) | `domain/<context>/repository` | `~Repository.kt` |
| Domain Gateway (interface) | `domain/<context>/gateway` | `~Gateway.kt` |
| Domain Event | `domain/<context>/event` | `~Event.kt` |
| Domain Exception | `domain/<context>/exception` | `~Exception.kt` |
| Domain Event Publisher (interface) | `domain/common` | `DomainEventPublisher.kt` |
| Spring Data JPA Repository | `infrastructure/<context>/mysql` | `~JpaRepository.kt` — `JpaRepository<도메인Entity, Long>` 직접 |
| Repository 구현 (QueryDSL) | `infrastructure/<context>/mysql` | `~RepositoryImpl.kt` |
| Mongo Repository 구현 | `infrastructure/<context>/mongo` | `~MongoRepositoryImpl.kt` |
| Gateway 구현 | `infrastructure/<context>/gateway` | `~GatewayImpl.kt` |
| Event Publisher 구현 (Kafka Producer) | `infrastructure/<context>/kafka` | `Kafka~Publisher.kt` |

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
Request (presentation/dto/request)
  → Command (application/dto)        — Request.toCommand()
    → Entity (domain/entity)         — Command → Entity 직접 생성
      → Result (application/dto)     — UseCase 반환
        → Response (presentation/dto/response)  — Controller 가 Response.of(result)
```

- Request: 외부 입력 형태. `toCommand()` 로 Command 변환.
- Command: UseCase 실행 파라미터.
- Result: UseCase 반환값 (도메인 Entity 또는 application 내부 DTO). presentation 타입을 application 이 알지 않는다.
- Response: Controller 가 `Result` → `Response.of(...)` 로 변환해 반환. **Response 는 presentation 소속** — application/domain 은 Response 를 import 하지 않는다.
- **Entity ↔ 영속화 POJO 변환 단계 없음** — JPA Entity 가 도메인 Entity 이므로 toEntity()/toDomain() 매퍼 금지. Command → Entity 직접 생성, Entity/Result → Response 직접 매핑.

### @Transactional 메서드는 Entity 가 아닌 DTO 를 반환한다 (OSIV 위반 방지)

`@Transactional` 이 걸린 메서드(application UseCase, domain DomainService 불문)는 **반환값으로 JPA Entity 를 내보내지 않는다.** 트랜잭션·영속성 컨텍스트가 닫힌 뒤 호출자가 Entity 의 lazy 연관/프록시에 접근하면 `LazyInitializationException` 이 나거나, 이를 가리려고 OSIV(Open Session In View) 를 켜게 된다. OSIV 는 커넥션을 뷰 렌더링까지 잡아 커넥션 풀을 고갈시키는 안티패턴이다.

| 위치 | 반환 |
|---|---|
| `@Transactional` UseCase.execute | **Response/Result DTO** (이미 컨벤션 — Entity → Response 매핑은 트랜잭션 안에서) |
| `@Transactional` DomainService 메서드 | **DTO 또는 primitive/Unit/식별자(Long)**. Entity 를 그대로 반환 금지 |

```kotlin
// ❌ BAD — @Transactional 메서드가 Entity 반환 (트랜잭션 밖에서 lazy 터짐 / OSIV 강제)
@Transactional
fun confirmOrder(orderId: Long, paymentId: Long): TicketOrder { ... }

// ✅ GOOD — DTO 로 반환 (트랜잭션 안에서 필요한 값 추출 완료)
@Transactional
fun confirmOrder(orderId: Long, paymentId: Long): ConfirmOrderResult { ... }
```

- 트랜잭션 경계 안에서 Entity → DTO 매핑을 끝내고 **detach 된 DTO 만 밖으로** 내보낸다.
- DomainService 가 Entity 를 UseCase 로 넘기고 UseCase(@Transactional)가 Response 로 매핑하는 기존 패턴은, 매핑이 **UseCase 트랜잭션 안에서** 일어나므로 허용된다. 다만 그 경우 DomainService 메서드 자체에는 `@Transactional` 을 중복으로 걸지 않는다 (트랜잭션은 UseCase 한 곳).
- **금지**: OSIV 활성화(`spring.jpa.open-in-view=true`)로 lazy 문제를 가리는 것. 기본 `false` 를 유지하고 DTO 반환으로 해결한다.

## 트랜잭션 & 이벤트

| 상황 | 위치 |
|---|---|
| 기본 트랜잭션 | UseCase `@Transactional` |
| Domain Event 발행 | DomainService가 `DomainEventPublisher` interface(domain layer)로 호출, 구현체는 infrastructure (Kafka·Spring ApplicationEventPublisher 등) |
| 이벤트 처리 | `@TransactionalEventListener(AFTER_COMMIT)` — **presentation/<context>/worker** 에 위치, UseCase 경유 |
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

- **위치**: `presentation/<context>/worker/~EventWorker.kt` — Controller와 동일한 외부 진입점
- `ConsumerRecord<String, String>` 금지 → DTO 직접 매핑
- `JsonDeserializer` + `trusted.packages` 설정
- Consumer에서 Repository / DomainService 직접 호출 금지 → **UseCase 경유**
- ObjectMapper 수동 파싱 금지

```kotlin
// presentation/<context>/worker/PlanChangedEventWorker.kt
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