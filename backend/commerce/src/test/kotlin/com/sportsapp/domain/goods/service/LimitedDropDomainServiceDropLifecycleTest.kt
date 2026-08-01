package com.sportsapp.domain.goods.service

import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.exception.LimitedDropQuantityExceedsStockException
import com.sportsapp.domain.goods.gateway.DropReservationStore
import com.sportsapp.domain.goods.repository.LimitedDropRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import java.time.ZonedDateTime

/**
 * [LimitedDropDomainService.openDrop] / [LimitedDropDomainService.createDrop] 회차 개설·상태 전이 시나리오.
 * [W1-DEBT-01] LimitedDropDomainServiceTest(LargeClass) 분리 — 회차 개설 흐름 전담.
 */
class LimitedDropDomainServiceDropLifecycleTest : BehaviorSpec({

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
        val ttlSlot = slot<Duration>()

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
        val product = sampleSneakerProduct()
        val productWithStock = ProductWithStock(product = product, stockQuantity = 50)
        val savedDropSlot = slot<LimitedDrop>()
        val ttlSlot = slot<Duration>()

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
        val product = sampleSneakerProduct()
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
        val product = sampleSneakerProduct()
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
})
