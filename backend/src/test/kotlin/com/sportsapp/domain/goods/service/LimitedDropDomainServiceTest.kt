package com.sportsapp.domain.goods.service

import com.sportsapp.domain.goods.vo.SellerType
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.RedisLockException
import com.sportsapp.domain.goods.dto.LimitedDropStats
import com.sportsapp.domain.goods.dto.PurchaseLimitedDropCommand
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.event.LimitedDropOversoldEvent
import com.sportsapp.domain.goods.exception.LimitedDropNotFoundException
import com.sportsapp.domain.goods.exception.LimitedDropPerUserLimitExceededException
import com.sportsapp.domain.goods.exception.LimitedDropQuantityExceedsStockException
import com.sportsapp.domain.goods.exception.LimitedDropSoldOutException
import com.sportsapp.domain.goods.exception.LimitedDropThrottledException
import com.sportsapp.domain.goods.exception.LimitedDropTooEarlyException
import com.sportsapp.domain.goods.gateway.DropReservationStore
import com.sportsapp.domain.goods.gateway.RejectCounts
import com.sportsapp.domain.goods.gateway.RejectKind
import com.sportsapp.domain.goods.gateway.ReservationResult
import com.sportsapp.domain.goods.repository.LimitedDropRepository
import com.sportsapp.domain.goods.vo.OrderItemInput
import com.sportsapp.domain.goods.vo.ProductCategory
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.dao.DataAccessResourceFailureException

private const val DROP_ID = 0L
private const val PRODUCT_ID = 10L
private const val USER_ID = 100L
private const val OWNER_USER_ID = 500L
private const val PER_USER_LIMIT = 2
private const val QUANTITY = 1
private const val IDEMPOTENCY_KEY = "idem-key-1"

class LimitedDropDomainServiceTest : BehaviorSpec({

    fun openDrop(): LimitedDrop = LimitedDrop.reconstitute(
        productId = PRODUCT_ID,
        openAt = ZonedDateTime.now().minusMinutes(1),
        closeAt = ZonedDateTime.now().plusDays(1),
        limitedQuantity = 100,
        perUserLimit = PER_USER_LIMIT,
        status = LimitedDropStatus.OPEN,
    )

    fun buildService(
        limitedDropRepository: LimitedDropRepository = mockk(),
        dropReservationStore: DropReservationStore = mockk(),
        goodsDomainService: GoodsDomainService = mockk(),
        domainEventPublisher: DomainEventPublisher = mockk(),
    ) = LimitedDropDomainService(
        limitedDropRepository = limitedDropRepository,
        dropReservationStore = dropReservationStore,
        goodsDomainService = goodsDomainService,
        domainEventPublisher = domainEventPublisher,
    )

    fun command(): PurchaseLimitedDropCommand = PurchaseLimitedDropCommand(
        dropId = DROP_ID,
        userId = USER_ID,
        quantity = QUANTITY,
        idempotencyKey = IDEMPOTENCY_KEY,
    )

    Given("Admitted 판정을 받은 구매 요청") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val drop = openDrop()
        val order = GoodsOrder.create(userId = USER_ID, totalAmount = BigDecimal("1000"), idempotencyKey = IDEMPOTENCY_KEY)

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every {
            dropReservationStore.reserve(DROP_ID, USER_ID, QUANTITY, PER_USER_LIMIT, IDEMPOTENCY_KEY)
        } returns ReservationResult.Admitted
        every { dropReservationStore.tryAcquireThrottle() } returns true
        every { dropReservationStore.releaseThrottle() } returns Unit
        every {
            goodsDomainService.createPendingOrder(USER_ID, listOf(OrderItemInput(PRODUCT_ID, QUANTITY)), IDEMPOTENCY_KEY)
        } returns order
        every { dropReservationStore.confirmSuccess(DROP_ID, USER_ID, IDEMPOTENCY_KEY) } returns Unit

        When("purchase를 호출하면") {
            val result = service.purchase(command())

            Then("완충 permit을 획득한 뒤 createPendingOrder를 호출하고 confirmSuccess·releaseThrottle로 반납한다") {
                result shouldBe (drop to order)
                verify(exactly = 1) { dropReservationStore.tryAcquireThrottle() }
                verify(exactly = 1) {
                    goodsDomainService.createPendingOrder(USER_ID, listOf(OrderItemInput(PRODUCT_ID, QUANTITY)), IDEMPOTENCY_KEY)
                }
                verify(exactly = 1) { dropReservationStore.confirmSuccess(DROP_ID, USER_ID, IDEMPOTENCY_KEY) }
                verify(exactly = 1) { dropReservationStore.releaseThrottle() }
            }
        }
    }

    Given("SoldOut 판정을 받은 구매 요청") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val drop = openDrop()

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every {
            dropReservationStore.reserve(DROP_ID, USER_ID, QUANTITY, PER_USER_LIMIT, IDEMPOTENCY_KEY)
        } returns ReservationResult.SoldOut
        every { dropReservationStore.recordReject(DROP_ID, RejectKind.SOLD_OUT) } returns Unit

        When("purchase를 호출하면") {
            Then("createPendingOrder를 호출하지 않고 LimitedDropSoldOutException을 던지며 sold-out 거부를 기록한다") {
                shouldThrow<LimitedDropSoldOutException> { service.purchase(command()) }
                verify(exactly = 0) { goodsDomainService.createPendingOrder(any(), any(), any()) }
                verify(exactly = 1) { dropReservationStore.recordReject(DROP_ID, RejectKind.SOLD_OUT) }
            }
        }
    }

    Given("Admitted 판정 이후 완충 permit이 소진된 구매 요청") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val drop = openDrop()

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every {
            dropReservationStore.reserve(DROP_ID, USER_ID, QUANTITY, PER_USER_LIMIT, IDEMPOTENCY_KEY)
        } returns ReservationResult.Admitted
        every { dropReservationStore.tryAcquireThrottle() } returns false
        every { dropReservationStore.cancel(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY) } returns Unit

        When("purchase를 호출하면") {
            Then("cancel로 Redis 슬롯을 복원하고 429로 매핑되는 LimitedDropThrottledException을 던지며 DB에 도달하지 않는다") {
                val exception = shouldThrow<LimitedDropThrottledException> { service.purchase(command()) }
                exception.status.httpStatus shouldBe 429
                verify(exactly = 1) { dropReservationStore.cancel(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY) }
                verify(exactly = 0) { goodsDomainService.createPendingOrder(any(), any(), any()) }
                verify(exactly = 0) { dropReservationStore.releaseThrottle() }
            }
        }
    }

    Given("Redis 장애로 fail-open됐고 완충 permit도 소진된 구매 요청") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val drop = openDrop()

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every {
            dropReservationStore.reserve(DROP_ID, USER_ID, QUANTITY, PER_USER_LIMIT, IDEMPOTENCY_KEY)
        } throws DataAccessResourceFailureException("redis down")
        every { dropReservationStore.tryAcquireThrottle() } returns false

        When("purchase를 호출하면") {
            Then("완충 게이트를 우회하지 않고 429로 매핑되는 LimitedDropThrottledException을 던지며 cancel·DB 모두 호출하지 않는다") {
                val exception = shouldThrow<LimitedDropThrottledException> { service.purchase(command()) }
                exception.status.httpStatus shouldBe 429
                verify(exactly = 1) { dropReservationStore.tryAcquireThrottle() }
                verify(exactly = 0) { dropReservationStore.cancel(any(), any(), any(), any()) }
                verify(exactly = 0) { goodsDomainService.createPendingOrder(any(), any(), any()) }
                verify(exactly = 0) { dropReservationStore.releaseThrottle() }
            }
        }
    }

    Given("PerUserLimitExceeded 판정을 받은 구매 요청") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val drop = openDrop()

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every {
            dropReservationStore.reserve(DROP_ID, USER_ID, QUANTITY, PER_USER_LIMIT, IDEMPOTENCY_KEY)
        } returns ReservationResult.PerUserLimitExceeded(limit = PER_USER_LIMIT)

        When("purchase를 호출하면") {
            Then("403으로 매핑되는 LimitedDropPerUserLimitExceededException을 던지고 DB에 도달하지 않는다") {
                val exception = shouldThrow<LimitedDropPerUserLimitExceededException> { service.purchase(command()) }
                exception.status.httpStatus shouldBe 403
                exception.limit shouldBe PER_USER_LIMIT
                verify(exactly = 0) { goodsDomainService.createPendingOrder(any(), any(), any()) }
            }
        }
    }

    Given("Admitted 판정 이후 createPendingOrder가 예외를 던지는 상황") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val drop = openDrop()
        val failure = IllegalStateException("stock deduction failed")

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every {
            dropReservationStore.reserve(DROP_ID, USER_ID, QUANTITY, PER_USER_LIMIT, IDEMPOTENCY_KEY)
        } returns ReservationResult.Admitted
        every { dropReservationStore.tryAcquireThrottle() } returns true
        every { dropReservationStore.releaseThrottle() } returns Unit
        every {
            goodsDomainService.createPendingOrder(USER_ID, listOf(OrderItemInput(PRODUCT_ID, QUANTITY)), IDEMPOTENCY_KEY)
        } throws failure
        every { dropReservationStore.cancel(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY) } returns Unit

        When("purchase를 호출하면") {
            Then("cancel로 슬롯을 복원하고 완충 permit도 반납한 뒤 원본 예외를 재전파한다") {
                val thrown = shouldThrow<IllegalStateException> { service.purchase(command()) }
                thrown shouldBe failure
                verify(exactly = 1) { dropReservationStore.cancel(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY) }
                verify(exactly = 1) { dropReservationStore.releaseThrottle() }
                verify(exactly = 0) { dropReservationStore.confirmSuccess(any(), any(), any()) }
            }
        }
    }

    Given("동일 idempotencyKey로 재요청해 AlreadyReserved가 반환되는 상황") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val drop = openDrop()
        val existingOrder = GoodsOrder.create(userId = USER_ID, totalAmount = BigDecimal("1000"), idempotencyKey = IDEMPOTENCY_KEY)

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every {
            dropReservationStore.reserve(DROP_ID, USER_ID, QUANTITY, PER_USER_LIMIT, IDEMPOTENCY_KEY)
        } returns ReservationResult.AlreadyReserved
        every { dropReservationStore.tryAcquireThrottle() } returns true
        every { dropReservationStore.releaseThrottle() } returns Unit
        every {
            goodsDomainService.createPendingOrder(USER_ID, listOf(OrderItemInput(PRODUCT_ID, QUANTITY)), IDEMPOTENCY_KEY)
        } returns existingOrder

        When("purchase를 재호출하면") {
            val result = service.purchase(command())

            Then("재-DECR 없이 기존 주문을 그대로 반환하되 완충 게이트는 통과하고, confirmSuccess·cancel은 호출하지 않는다") {
                result shouldBe (drop to existingOrder)
                verify(exactly = 1) { dropReservationStore.reserve(DROP_ID, USER_ID, QUANTITY, PER_USER_LIMIT, IDEMPOTENCY_KEY) }
                verify(exactly = 1) { dropReservationStore.tryAcquireThrottle() }
                verify(exactly = 1) { dropReservationStore.releaseThrottle() }
                verify(exactly = 0) { dropReservationStore.confirmSuccess(any(), any(), any()) }
                verify(exactly = 0) { dropReservationStore.cancel(any(), any(), any(), any()) }
            }
        }
    }

    Given("아직 openAt이 도래하지 않은 회차에 대한 구매 요청") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val notYetOpenDrop = LimitedDrop.reconstitute(
            productId = PRODUCT_ID,
            openAt = ZonedDateTime.now().plusDays(1),
            closeAt = ZonedDateTime.now().plusDays(2),
            limitedQuantity = 100,
            perUserLimit = PER_USER_LIMIT,
            status = LimitedDropStatus.SCHEDULED,
        )

        every { limitedDropRepository.findById(DROP_ID) } returns notYetOpenDrop
        every { dropReservationStore.recordReject(DROP_ID, RejectKind.TOO_EARLY) } returns Unit

        When("purchase를 호출하면") {
            Then("reserve를 호출하지 않고 425로 매핑되는 LimitedDropTooEarlyException을 던지며 too-early 거부를 기록한다") {
                val exception = shouldThrow<LimitedDropTooEarlyException> { service.purchase(command()) }
                exception.status.httpStatus shouldBe 425
                exception.openAt shouldBe notYetOpenDrop.openAt
                verify(exactly = 0) { dropReservationStore.reserve(any(), any(), any(), any(), any()) }
                verify(exactly = 1) { dropReservationStore.recordReject(DROP_ID, RejectKind.TOO_EARLY) }
            }
        }
    }

    Given("reserve 호출 시 Redis가 DataAccessException을 던지는 상황") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val drop = openDrop()
        val order = GoodsOrder.create(userId = USER_ID, totalAmount = BigDecimal("1000"), idempotencyKey = IDEMPOTENCY_KEY)

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every {
            dropReservationStore.reserve(DROP_ID, USER_ID, QUANTITY, PER_USER_LIMIT, IDEMPOTENCY_KEY)
        } throws DataAccessResourceFailureException("redis down")
        every { dropReservationStore.tryAcquireThrottle() } returns true
        every { dropReservationStore.releaseThrottle() } returns Unit
        every {
            goodsDomainService.createPendingOrder(USER_ID, listOf(OrderItemInput(PRODUCT_ID, QUANTITY)), IDEMPOTENCY_KEY)
        } returns order

        When("purchase를 호출하면") {
            val result = service.purchase(command())

            Then("fail-open으로 Redis 게이트를 우회하되 완충 permit은 거쳐 createPendingOrder를 진행한다") {
                result shouldBe (drop to order)
                verify(exactly = 1) { dropReservationStore.tryAcquireThrottle() }
                verify(exactly = 1) { dropReservationStore.releaseThrottle() }
                verify(exactly = 1) {
                    goodsDomainService.createPendingOrder(USER_ID, listOf(OrderItemInput(PRODUCT_ID, QUANTITY)), IDEMPOTENCY_KEY)
                }
                verify(exactly = 0) { dropReservationStore.confirmSuccess(any(), any(), any()) }
                verify(exactly = 0) { dropReservationStore.cancel(any(), any(), any(), any()) }
            }
        }
    }

    Given("reserve 호출 시 RedisLockException이 발생하는 상황") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val drop = openDrop()
        val order = GoodsOrder.create(userId = USER_ID, totalAmount = BigDecimal("1000"), idempotencyKey = IDEMPOTENCY_KEY)

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every {
            dropReservationStore.reserve(DROP_ID, USER_ID, QUANTITY, PER_USER_LIMIT, IDEMPOTENCY_KEY)
        } throws RedisLockException("lock timeout")
        every { dropReservationStore.tryAcquireThrottle() } returns true
        every { dropReservationStore.releaseThrottle() } returns Unit
        every {
            goodsDomainService.createPendingOrder(USER_ID, listOf(OrderItemInput(PRODUCT_ID, QUANTITY)), IDEMPOTENCY_KEY)
        } returns order

        When("purchase를 호출하면") {
            val result = service.purchase(command())

            Then("fail-open으로 Redis 게이트를 우회하되 완충 permit은 거쳐 createPendingOrder를 진행한다") {
                result shouldBe (drop to order)
            }
        }
    }

    Given("product에 대해 OPEN 회차를 찾을 수 없는 구매 요청") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)

        every { limitedDropRepository.findById(DROP_ID) } returns null

        When("purchase를 호출하면") {
            Then("com.sportsapp.domain.goods.exception.LimitedDropNotFoundException을 던진다") {
                shouldThrow<com.sportsapp.domain.goods.exception.LimitedDropNotFoundException> {
                    service.purchase(command())
                }
            }
        }
    }

    Given("SCHEDULED 상태의 회차를 개설하는 상황") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val scheduledDrop = LimitedDrop.reconstitute(
            productId = PRODUCT_ID,
            openAt = ZonedDateTime.now().minusMinutes(1),
            closeAt = ZonedDateTime.now().plusHours(2),
            limitedQuantity = 50,
            perUserLimit = PER_USER_LIMIT,
            status = LimitedDropStatus.SCHEDULED,
        )
        val ttlSlot = slot<java.time.Duration>()

        every { limitedDropRepository.findById(DROP_ID) } returns scheduledDrop
        every { limitedDropRepository.save(scheduledDrop) } returns scheduledDrop
        every { dropReservationStore.seedIfAbsent(DROP_ID, 50, capture(ttlSlot)) } returns Unit

        When("openDrop을 호출하면") {
            val result = service.openDrop(DROP_ID)

            Then("OPEN으로 전이되고 (closeAt-now)+1h TTL로 Redis 카운터를 시드한다") {
                result.currentStatus shouldBe LimitedDropStatus.OPEN
                verify(exactly = 1) { dropReservationStore.seedIfAbsent(DROP_ID, 50, any()) }
                ttlSlot.captured.toMinutes() shouldBe 179L
            }
        }
    }

    Given("재고 이내의 수량으로 회차를 개설하는 상황") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val openAt = ZonedDateTime.now().plusHours(1)
        val closeAt = ZonedDateTime.now().plusHours(3)
        val limitedQuantity = 30
        val product = Product.create(
            name = "한정판 스니커즈",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("50000"),
            description = "설명",
            imageUrl = "https://image",
            ownerUserId = OWNER_USER_ID,
            sellerType = SellerType.B2C,
        )
        val productWithStock = ProductWithStock(product = product, stockQuantity = 50)
        val savedDropSlot = slot<LimitedDrop>()
        val ttlSlot = slot<java.time.Duration>()

        every { goodsDomainService.getProductWithStock(PRODUCT_ID) } returns productWithStock
        every { limitedDropRepository.save(capture(savedDropSlot)) } answers { savedDropSlot.captured }
        every { dropReservationStore.seedIfAbsent(0L, limitedQuantity, capture(ttlSlot)) } returns Unit

        When("createDrop을 호출하면") {
            val result = service.createDrop(
                productId = PRODUCT_ID,
                openAt = openAt,
                closeAt = closeAt,
                limitedQuantity = limitedQuantity,
                perUserLimit = PER_USER_LIMIT,
                ownerUserId = OWNER_USER_ID,
            )

            Then("SCHEDULED 상태로 저장하고 Redis 카운터를 limitedQuantity로 시드한다") {
                result.first.currentStatus shouldBe LimitedDropStatus.SCHEDULED
                result.first.productId shouldBe PRODUCT_ID
                verify(exactly = 1) { limitedDropRepository.save(any()) }
                verify(exactly = 1) { dropReservationStore.seedIfAbsent(0L, limitedQuantity, any()) }
            }

            Then("상품 가격을 함께 반환한다") {
                result.second shouldBe product.price
            }
        }
    }

    Given("limitedQuantity가 현재 재고를 초과하는 회차 개설 요청") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val openAt = ZonedDateTime.now().plusHours(1)
        val closeAt = ZonedDateTime.now().plusHours(3)
        val product = Product.create(
            name = "한정판 스니커즈",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("50000"),
            description = "설명",
            imageUrl = "https://image",
            ownerUserId = OWNER_USER_ID,
            sellerType = SellerType.B2C,
        )
        val productWithStock = ProductWithStock(product = product, stockQuantity = 10)

        every { goodsDomainService.getProductWithStock(PRODUCT_ID) } returns productWithStock

        When("createDrop을 호출하면") {
            Then("LimitedDropQuantityExceedsStockException을 던지고 저장·시드를 수행하지 않는다") {
                shouldThrow<LimitedDropQuantityExceedsStockException> {
                    service.createDrop(
                        productId = PRODUCT_ID,
                        openAt = openAt,
                        closeAt = closeAt,
                        limitedQuantity = 20,
                        perUserLimit = PER_USER_LIMIT,
                        ownerUserId = OWNER_USER_ID,
                    )
                }
                verify(exactly = 0) { limitedDropRepository.save(any()) }
                verify(exactly = 0) { dropReservationStore.seedIfAbsent(any(), any(), any()) }
            }
        }
    }

    Given("openAt이 closeAt보다 늦거나 같은 회차 개설 요청") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val openAt = ZonedDateTime.now().plusHours(3)
        val closeAt = ZonedDateTime.now().plusHours(1)
        val product = Product.create(
            name = "한정판 스니커즈",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("50000"),
            description = "설명",
            imageUrl = "https://image",
            ownerUserId = OWNER_USER_ID,
            sellerType = SellerType.B2C,
        )
        val productWithStock = ProductWithStock(product = product, stockQuantity = 100)

        every { goodsDomainService.getProductWithStock(PRODUCT_ID) } returns productWithStock

        When("createDrop을 호출하면") {
            Then("IllegalArgumentException으로 생성 검증에서 거부되고 저장을 수행하지 않는다") {
                shouldThrow<IllegalArgumentException> {
                    service.createDrop(
                        productId = PRODUCT_ID,
                        openAt = openAt,
                        closeAt = closeAt,
                        limitedQuantity = 30,
                        perUserLimit = PER_USER_LIMIT,
                        ownerUserId = OWNER_USER_ID,
                    )
                }
                verify(exactly = 0) { limitedDropRepository.save(any()) }
            }
        }
    }

    Given("sold-out 거부 상황에서 recordReject가 Redis 장애로 실패하는 상황") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val drop = openDrop()

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every {
            dropReservationStore.reserve(DROP_ID, USER_ID, QUANTITY, PER_USER_LIMIT, IDEMPOTENCY_KEY)
        } returns ReservationResult.SoldOut
        every {
            dropReservationStore.recordReject(DROP_ID, RejectKind.SOLD_OUT)
        } throws DataAccessResourceFailureException("redis down")

        When("purchase를 호출하면") {
            Then("카운터 실패를 무시(fail-open)하고 원래의 LimitedDropSoldOutException을 그대로 던진다") {
                shouldThrow<LimitedDropSoldOutException> { service.purchase(command()) }
            }
        }
    }

    Given("존재하는 회차의 조회 요청") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val drop = openDrop()
        val product = Product.create(
            name = "한정판 스니커즈",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("50000"),
            description = "설명",
            imageUrl = "https://image",
            ownerUserId = OWNER_USER_ID,
            sellerType = SellerType.B2C,
        )
        val productWithStock = ProductWithStock(product = product, stockQuantity = 42)

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every { dropReservationStore.remaining(DROP_ID) } returns 42
        every { goodsDomainService.getProductWithStock(PRODUCT_ID) } returns productWithStock

        When("getView를 호출하면") {
            val result = service.getView(DROP_ID)

            Then("drop과 Redis remaining·상품 가격을 그대로 결합해 반환한다") {
                result shouldBe Triple(drop, 42, product.price)
            }
        }
    }

    Given("존재하지 않는 dropId의 조회 요청") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)

        every { limitedDropRepository.findById(DROP_ID) } returns null

        When("getView를 호출하면") {
            Then("LimitedDropNotFoundException을 던진다") {
                shouldThrow<LimitedDropNotFoundException> { service.getView(DROP_ID) }
            }
        }

        When("getStats를 호출하면") {
            Then("LimitedDropNotFoundException을 던진다") {
                shouldThrow<LimitedDropNotFoundException> { service.getStats(DROP_ID) }
            }
        }
    }

    Given("성공·소진거부·시작전거부가 섞인 회차의 집계 요청") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val drop = openDrop()

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every { dropReservationStore.remaining(DROP_ID) } returns 30
        every { dropReservationStore.rejectCounts(DROP_ID) } returns RejectCounts(soldOutCount = 5, tooEarlyCount = 7)

        When("getStats를 호출하면") {
            val result = service.getStats(DROP_ID)

            Then("successCount는 limitedQuantity-remaining이고 거부 건수는 그대로 결합된다") {
                result shouldBe LimitedDropStats(
                    successCount = 70,
                    soldOutRejectCount = 5,
                    tooEarlyRejectCount = 7,
                )
            }
        }
    }

    Given("시드되지 않아 remaining이 null인 회차의 집계 요청") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val scheduledDrop = LimitedDrop.reconstitute(
            productId = PRODUCT_ID,
            openAt = ZonedDateTime.now().plusHours(1),
            closeAt = ZonedDateTime.now().plusHours(3),
            limitedQuantity = 50,
            perUserLimit = PER_USER_LIMIT,
            status = LimitedDropStatus.SCHEDULED,
        )

        every { limitedDropRepository.findById(DROP_ID) } returns scheduledDrop
        every { dropReservationStore.remaining(DROP_ID) } returns null
        every { dropReservationStore.rejectCounts(DROP_ID) } returns RejectCounts(soldOutCount = 0, tooEarlyCount = 0)

        When("getStats를 호출하면") {
            val result = service.getStats(DROP_ID)

            Then("remaining을 limitedQuantity로 간주해 successCount 0을 반환한다") {
                result shouldBe LimitedDropStats(
                    successCount = 0,
                    soldOutRejectCount = 0,
                    tooEarlyRejectCount = 0,
                )
            }
        }
    }

    Given("Redis remaining 드리프트로 계산된 판매량이 limitedQuantity를 초과하는 회차") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService, domainEventPublisher)
        val drop = openDrop()
        val product = Product.create(
            name = "한정판 스니커즈",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("50000"),
            description = "설명",
            imageUrl = "https://image",
            ownerUserId = OWNER_USER_ID,
            sellerType = SellerType.B2C,
        )
        val eventsSlot = slot<List<com.sportsapp.domain.common.DomainEvent>>()

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every { dropReservationStore.remaining(DROP_ID) } returns -5
        every { goodsDomainService.getProductWithStock(PRODUCT_ID) } returns ProductWithStock(product = product, stockQuantity = 50)
        every { domainEventPublisher.publishAll(capture(eventsSlot)) } returns Unit

        When("reconcile를 호출하면") {
            service.reconcile(DROP_ID)

            Then("LimitedDropOversoldEvent를 발행한다") {
                eventsSlot.captured shouldHaveSize 1
                val event = eventsSlot.captured[0] as LimitedDropOversoldEvent
                event.dropId shouldBe DROP_ID
                event.productId shouldBe PRODUCT_ID
                event.detectedQuantity shouldBe 105
            }
        }
    }

    Given("Redis remaining과 DB 재고가 모두 정상인 회차") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService, domainEventPublisher)
        val drop = openDrop()
        val product = Product.create(
            name = "한정판 스니커즈",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("50000"),
            description = "설명",
            imageUrl = "https://image",
            ownerUserId = OWNER_USER_ID,
            sellerType = SellerType.B2C,
        )

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every { dropReservationStore.remaining(DROP_ID) } returns 30
        every { goodsDomainService.getProductWithStock(PRODUCT_ID) } returns ProductWithStock(product = product, stockQuantity = 50)

        When("reconcile를 호출하면") {
            service.reconcile(DROP_ID)

            Then("이벤트를 발행하지 않는다") {
                verify(exactly = 0) { domainEventPublisher.publishAll(any()) }
                verify(exactly = 0) { domainEventPublisher.publish(any()) }
            }
        }
    }

    Given("활성 회차 2건이 존재하는 상황") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService, domainEventPublisher)
        val firstDrop = openDrop()
        val secondDrop = LimitedDrop.reconstitute(
            productId = PRODUCT_ID + 1,
            openAt = ZonedDateTime.now().minusMinutes(1),
            closeAt = ZonedDateTime.now().plusDays(1),
            limitedQuantity = 50,
            perUserLimit = PER_USER_LIMIT,
            status = LimitedDropStatus.OPEN,
        )
        val product = Product.create(
            name = "한정판 스니커즈",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("50000"),
            description = "설명",
            imageUrl = "https://image",
            ownerUserId = OWNER_USER_ID,
            sellerType = SellerType.B2C,
        )

        every { limitedDropRepository.findAllActive() } returns listOf(firstDrop, secondDrop)
        every { dropReservationStore.remaining(any()) } returns 30
        every { goodsDomainService.getProductWithStock(PRODUCT_ID) } returns ProductWithStock(product = product, stockQuantity = 50)
        every { goodsDomainService.getProductWithStock(PRODUCT_ID + 1) } returns ProductWithStock(product = product, stockQuantity = 50)

        When("reconcileAllActive를 호출하면") {
            service.reconcileAllActive()

            Then("활성 회차 각각에 대해 대사를 수행한다") {
                verify(exactly = 2) { dropReservationStore.remaining(any()) }
                verify(exactly = 1) { goodsDomainService.getProductWithStock(PRODUCT_ID) }
                verify(exactly = 1) { goodsDomainService.getProductWithStock(PRODUCT_ID + 1) }
            }
        }
    }

    Given("활성 회차가 존재하지 않는 상황") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)

        every { limitedDropRepository.findAllActive() } returns emptyList()

        When("reconcileAllActive를 호출하면") {
            service.reconcileAllActive()

            Then("Redis·상품 조회를 전혀 수행하지 않고 조기 반환한다") {
                verify(exactly = 0) { dropReservationStore.remaining(any()) }
                verify(exactly = 0) { goodsDomainService.getProductWithStock(any()) }
            }
        }
    }

    Given("활성 회차 2건 중 첫 번째 회차의 Redis 조회가 장애 상태인 상황") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService, domainEventPublisher)
        val firstDrop = openDrop()
        val secondDrop = LimitedDrop.reconstitute(
            productId = PRODUCT_ID + 1,
            openAt = ZonedDateTime.now().minusMinutes(1),
            closeAt = ZonedDateTime.now().plusDays(1),
            limitedQuantity = 50,
            perUserLimit = PER_USER_LIMIT,
            status = LimitedDropStatus.OPEN,
        )
        val product = Product.create(
            name = "한정판 스니커즈",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("50000"),
            description = "설명",
            imageUrl = "https://image",
            ownerUserId = OWNER_USER_ID,
            sellerType = SellerType.B2C,
        )

        every { limitedDropRepository.findAllActive() } returns listOf(firstDrop, secondDrop)
        every { dropReservationStore.remaining(DROP_ID) } throws
            DataAccessResourceFailureException("redis down") andThen 30
        every { goodsDomainService.getProductWithStock(PRODUCT_ID + 1) } returns
            ProductWithStock(product = product, stockQuantity = 50)

        When("reconcileAllActive를 호출하면") {
            Then("예외를 전파하지 않고 나머지 회차 대사를 계속 수행한다") {
                shouldNotThrowAny { service.reconcileAllActive() }
                verify(exactly = 2) { dropReservationStore.remaining(DROP_ID) }
                verify(exactly = 0) { goodsDomainService.getProductWithStock(PRODUCT_ID) }
                verify(exactly = 1) { goodsDomainService.getProductWithStock(PRODUCT_ID + 1) }
            }
        }
    }
})
