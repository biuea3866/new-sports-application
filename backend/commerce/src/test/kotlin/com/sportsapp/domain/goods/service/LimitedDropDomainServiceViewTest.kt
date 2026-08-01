package com.sportsapp.domain.goods.service

import com.sportsapp.domain.goods.dto.LimitedDropStats
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.exception.LimitedDropNotFoundException
import com.sportsapp.domain.goods.gateway.DropReservationStore
import com.sportsapp.domain.goods.gateway.RejectCounts
import com.sportsapp.domain.goods.repository.LimitedDropRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

/**
 * [LimitedDropDomainService.getView] / [LimitedDropDomainService.getStats] 조회·집계 시나리오.
 * [W1-DEBT-01] LimitedDropDomainServiceTest(LargeClass) 분리 — 조회 흐름 전담.
 */
class LimitedDropDomainServiceViewTest : BehaviorSpec({

    Given("존재하는 회차의 조회 요청") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val drop = openDrop()
        val product = sampleSneakerProduct()
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
})
