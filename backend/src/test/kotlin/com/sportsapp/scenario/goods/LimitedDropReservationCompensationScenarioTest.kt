package com.sportsapp.scenario.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.goods.usecase.PurchaseLimitedDropUseCase
import com.sportsapp.domain.goods.dto.PurchaseLimitedDropCommand
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.domain.goods.gateway.DropReservationStore
import com.sportsapp.domain.goods.service.LimitedDropDomainService
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.infrastructure.goods.mysql.GoodsOrderJpaRepository
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import com.sportsapp.infrastructure.goods.mysql.StockJpaRepository
import com.sportsapp.infrastructure.goods.redis.DropReservationStoreImpl
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

private const val OWNER_USER_ID = 9_100L
private const val BUYER_USER_ID = 9_200L

/**
 * [DropReservationStore.releaseThrottle]는 `persistWithThrottle`의 `finally`에서 성공·실패·
 * Admitted·AlreadyReserved 여부와 무관하게 DB 쓰기를 시도한 모든 시도에서 호출된다 —
 * `confirmSuccess`(restoreOnFailure=true인 첫 Admitted 시도에서만 호출)와 달리, 재시도 시퀀스의
 * 모든 시도(AlreadyReserved로 이어지는 재시도 포함)를 빠짐없이 가로챌 수 있는 지점이다. 여기서
 * `beforeCommit`에 예외를 등록하면 "커밋 단계 실패"를 결정적으로 재현할 수 있다(FIX-02 티켓
 * 재현안 B) — 매 호출마다 [remainingFailures]를 소비해 실패 횟수를 정확히 통제한다.
 */
class CommitFailureInjectingDropReservationStore(
    private val delegate: DropReservationStore,
    private val remainingFailures: AtomicInteger,
) : DropReservationStore by delegate {

    override fun releaseThrottle() {
        val shouldFail = remainingFailures.getAndUpdate { current -> if (current > 0) current - 1 else 0 } > 0
        if (shouldFail) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun beforeCommit(readOnly: Boolean) {
                    throw ObjectOptimisticLockingFailureException("Stock", 0L)
                }
            })
        }
        delegate.releaseThrottle()
    }
}

@TestConfiguration
class CommitFailureInjectionTestConfig {

    @Bean
    fun commitFailureBudget(): AtomicInteger = AtomicInteger(0)

    @Bean
    @Primary
    fun commitFailureInjectingDropReservationStore(
        real: DropReservationStoreImpl,
        commitFailureBudget: AtomicInteger,
    ): DropReservationStore = CommitFailureInjectingDropReservationStore(real, commitFailureBudget)
}

/**
 * FIX-02 — F1(예약 보상 원자성) 커밋 단계 실패·재시도 예산 소진까지 실 MySQL·Redis(Testcontainers)로
 * 검증하는 시나리오 테스트. RED-1(단일/반복 커밋 단계 실패 → 보상)·RED-2(재시도 예산 소진 → 예약
 * 누수 0건)를 재현한다. 재시도 예산은 `app.limited-drop.retry.max-attempts=3`으로 낮춰 결정적으로
 * 검증한다.
 */
@Import(CommitFailureInjectionTestConfig::class)
@TestPropertySource(properties = ["app.limited-drop.retry.max-attempts=3"])
class LimitedDropReservationCompensationScenarioTest(
    @Autowired private val purchaseLimitedDropUseCase: PurchaseLimitedDropUseCase,
    @Autowired private val limitedDropDomainService: LimitedDropDomainService,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val goodsOrderJpaRepository: GoodsOrderJpaRepository,
    @Autowired private val redisTemplate: StringRedisTemplate,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val commitFailureBudget: AtomicInteger,
) : BaseIntegrationTest() {

    init {
        fun remainingKey(dropId: Long) = "goods:limited-drop:$dropId:remaining"
        fun buyerKey(dropId: Long, userId: Long) = "goods:limited-drop:$dropId:buyer:$userId"
        fun reservedKey(dropId: Long, idempotencyKey: String) = "goods:limited-drop:$dropId:reserved:$idempotencyKey"

        fun createDropWithStock(limitedQuantity: Int): Long {
            val product = productJpaRepository.save(
                Product(
                    name = "보상검증상품",
                    category = ProductCategory.FOOTWEAR,
                    price = BigDecimal("10000"),
                    description = "설명",
                    imageUrl = "https://example.com/x.jpg",
                    status = ProductStatus.ACTIVE,
                    ownerId = OWNER_USER_ID,
                )
            )
            stockJpaRepository.save(Stock(productId = product.id, quantity = limitedQuantity))
            val (drop, _) = limitedDropDomainService.createDrop(
                productId = product.id,
                openAt = ZonedDateTime.now().minusMinutes(1),
                closeAt = ZonedDateTime.now().plusDays(1),
                limitedQuantity = limitedQuantity,
                perUserLimit = 5,
                ownerUserId = OWNER_USER_ID,
            )
            return drop.id
        }

        beforeEach {
            jdbcTemplate.execute("DELETE FROM goods_order_items")
            jdbcTemplate.execute("DELETE FROM goods_orders")
            jdbcTemplate.execute("DELETE FROM limited_drops")
            jdbcTemplate.execute("DELETE FROM stocks")
            jdbcTemplate.execute("DELETE FROM products")
            commitFailureBudget.set(0)
        }

        Given("재시도 예산(3회)을 전부 소진할 만큼 커밋 단계 실패가 반복되는 회차") {
            When("PurchaseLimitedDropUseCase.execute를 호출하면") {
                Then("[RED-1][RED-2] 예약이 취소되고 remaining·buyer가 원복되며 주문이 생성되지 않는다") {
                    val dropId = createDropWithStock(limitedQuantity = 10)
                    val idempotencyKey = UUID.randomUUID().toString()
                    commitFailureBudget.set(3)

                    shouldThrow<ObjectOptimisticLockingFailureException> {
                        purchaseLimitedDropUseCase.execute(
                            PurchaseLimitedDropCommand(
                                dropId = dropId,
                                userId = BUYER_USER_ID,
                                quantity = 1,
                                idempotencyKey = idempotencyKey,
                            ),
                        )
                    }

                    redisTemplate.opsForValue().get(remainingKey(dropId)) shouldBe "10"
                    redisTemplate.opsForValue().get(buyerKey(dropId, BUYER_USER_ID)) shouldBe "0"
                    redisTemplate.hasKey(reservedKey(dropId, idempotencyKey)) shouldBe false
                    goodsOrderJpaRepository.count() shouldBe 0
                }
            }
        }

        Given("2회 커밋 단계 실패 후 3번째 시도에서 성공하는 회차") {
            When("PurchaseLimitedDropUseCase.execute를 호출하면") {
                Then("[Test 6] 주문이 정확히 1건 생성되고 remaining은 1만 차감된다(중간 롤백에서 예약 유지)") {
                    val dropId = createDropWithStock(limitedQuantity = 10)
                    val idempotencyKey = UUID.randomUUID().toString()
                    commitFailureBudget.set(2)

                    purchaseLimitedDropUseCase.execute(
                        PurchaseLimitedDropCommand(
                            dropId = dropId,
                            userId = BUYER_USER_ID,
                            quantity = 1,
                            idempotencyKey = idempotencyKey,
                        ),
                    )

                    redisTemplate.opsForValue().get(remainingKey(dropId)) shouldBe "9"
                    redisTemplate.opsForValue().get(buyerKey(dropId, BUYER_USER_ID)) shouldBe "1"
                    redisTemplate.hasKey(reservedKey(dropId, idempotencyKey)) shouldBe true
                    goodsOrderJpaRepository.count() shouldBe 1
                }
            }
        }
    }
}
