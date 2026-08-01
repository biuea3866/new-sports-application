package com.sportsapp.domain.goods.service

import com.sportsapp.domain.common.exceptions.RedisLockException
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.exception.LimitedDropPerUserLimitExceededException
import com.sportsapp.domain.goods.exception.LimitedDropSoldOutException
import com.sportsapp.domain.goods.exception.LimitedDropThrottledException
import com.sportsapp.domain.goods.exception.LimitedDropTooEarlyException
import com.sportsapp.domain.goods.gateway.DropReservationCompensator
import com.sportsapp.domain.goods.gateway.DropReservationStore
import com.sportsapp.domain.goods.gateway.RejectKind
import com.sportsapp.domain.goods.gateway.ReservationResult
import com.sportsapp.domain.goods.repository.LimitedDropRepository
import com.sportsapp.domain.goods.vo.OrderItemInput
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.dao.DataAccessResourceFailureException

/**
 * [LimitedDropDomainService.purchase] 구매 판정·완충·롤백 보상 시나리오.
 * [W1-DEBT-01] LimitedDropDomainServiceTest(LargeClass) 분리 — 구매 흐름 전담.
 */
class LimitedDropDomainServicePurchaseTest : BehaviorSpec({

    Given("Admitted 판정을 받은 구매 요청") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val dropReservationCompensator = mockk<DropReservationCompensator>()
        val service = buildService(
            limitedDropRepository,
            dropReservationStore,
            goodsDomainService,
            dropReservationCompensator = dropReservationCompensator,
        )
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
        every {
            dropReservationCompensator.registerCancelOnRollback(
                dropId = DROP_ID,
                userId = USER_ID,
                quantity = QUANTITY,
                idempotencyKey = IDEMPOTENCY_KEY,
                admittedThisAttempt = true,
            )
        } returns Unit

        When("purchase를 호출하면") {
            val result = service.purchase(purchaseCommand())

            Then("완충 permit을 획득한 뒤 createPendingOrder를 호출하고 confirmSuccess·releaseThrottle로 반납한다") {
                result shouldBe (drop to order)
                verify(exactly = 1) { dropReservationStore.tryAcquireThrottle() }
                verify(exactly = 1) {
                    goodsDomainService.createPendingOrder(USER_ID, listOf(OrderItemInput(PRODUCT_ID, QUANTITY)), IDEMPOTENCY_KEY)
                }
                verify(exactly = 1) { dropReservationStore.confirmSuccess(DROP_ID, USER_ID, IDEMPOTENCY_KEY) }
                verify(exactly = 1) { dropReservationStore.releaseThrottle() }
            }

            Then("[FIX-02] 커밋 단계 실패까지 포괄하는 롤백 보상을 admittedThisAttempt=true로 등록한다") {
                verify(exactly = 1) {
                    dropReservationCompensator.registerCancelOnRollback(
                        DROP_ID,
                        USER_ID,
                        QUANTITY,
                        IDEMPOTENCY_KEY,
                        admittedThisAttempt = true,
                    )
                }
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
                shouldThrow<LimitedDropSoldOutException> { service.purchase(purchaseCommand()) }
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
                val exception = shouldThrow<LimitedDropThrottledException> { service.purchase(purchaseCommand()) }
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
        val dropReservationCompensator = mockk<DropReservationCompensator>()
        val service = buildService(
            limitedDropRepository,
            dropReservationStore,
            goodsDomainService,
            dropReservationCompensator = dropReservationCompensator,
        )
        val drop = openDrop()

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every {
            dropReservationStore.reserve(DROP_ID, USER_ID, QUANTITY, PER_USER_LIMIT, IDEMPOTENCY_KEY)
        } throws DataAccessResourceFailureException("redis down")
        every { dropReservationStore.tryAcquireThrottle() } returns false

        When("purchase를 호출하면") {
            Then("완충 게이트를 우회하지 않고 429로 매핑되는 LimitedDropThrottledException을 던지며 cancel·DB 모두 호출하지 않는다") {
                val exception = shouldThrow<LimitedDropThrottledException> { service.purchase(purchaseCommand()) }
                exception.status.httpStatus shouldBe 429
                verify(exactly = 1) { dropReservationStore.tryAcquireThrottle() }
                verify(exactly = 0) { dropReservationStore.cancel(any(), any(), any(), any()) }
                verify(exactly = 0) { goodsDomainService.createPendingOrder(any(), any(), any()) }
                verify(exactly = 0) { dropReservationStore.releaseThrottle() }
            }

            Then("[FIX-02] 예약이 없으므로 롤백 보상을 등록하지 않는다") {
                verify(exactly = 0) { dropReservationCompensator.registerCancelOnRollback(any(), any(), any(), any(), any()) }
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
                val exception = shouldThrow<LimitedDropPerUserLimitExceededException> { service.purchase(purchaseCommand()) }
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
                val thrown = shouldThrow<IllegalStateException> { service.purchase(purchaseCommand()) }
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
        val dropReservationCompensator = mockk<DropReservationCompensator>()
        val service = buildService(
            limitedDropRepository,
            dropReservationStore,
            goodsDomainService,
            dropReservationCompensator = dropReservationCompensator,
        )
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
        every {
            dropReservationCompensator.registerCancelOnRollback(
                dropId = DROP_ID,
                userId = USER_ID,
                quantity = QUANTITY,
                idempotencyKey = IDEMPOTENCY_KEY,
                admittedThisAttempt = false,
            )
        } returns Unit

        When("purchase를 재호출하면") {
            val result = service.purchase(purchaseCommand())

            Then("재-DECR 없이 기존 주문을 그대로 반환하되 완충 게이트는 통과하고, confirmSuccess·cancel은 호출하지 않는다") {
                result shouldBe (drop to existingOrder)
                verify(exactly = 1) { dropReservationStore.reserve(DROP_ID, USER_ID, QUANTITY, PER_USER_LIMIT, IDEMPOTENCY_KEY) }
                verify(exactly = 1) { dropReservationStore.tryAcquireThrottle() }
                verify(exactly = 1) { dropReservationStore.releaseThrottle() }
                verify(exactly = 0) { dropReservationStore.confirmSuccess(any(), any(), any()) }
                verify(exactly = 0) { dropReservationStore.cancel(any(), any(), any(), any()) }
            }

            Then("[FIX-02] 롤백 보상은 admittedThisAttempt=false로 등록한다 (같은 시퀀스가 아니면 구현체가 무시)") {
                verify(exactly = 1) {
                    dropReservationCompensator.registerCancelOnRollback(
                        DROP_ID,
                        USER_ID,
                        QUANTITY,
                        IDEMPOTENCY_KEY,
                        admittedThisAttempt = false,
                    )
                }
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
                val exception = shouldThrow<LimitedDropTooEarlyException> { service.purchase(purchaseCommand()) }
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
            val result = service.purchase(purchaseCommand())

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
            val result = service.purchase(purchaseCommand())

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
                    service.purchase(purchaseCommand())
                }
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
                shouldThrow<LimitedDropSoldOutException> { service.purchase(purchaseCommand()) }
            }
        }
    }
})
