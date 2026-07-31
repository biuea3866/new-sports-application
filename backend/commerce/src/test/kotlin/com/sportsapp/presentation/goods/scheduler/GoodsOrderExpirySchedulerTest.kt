package com.sportsapp.presentation.goods.scheduler

import com.sportsapp.application.goods.dto.GoodsOrderExpiryResult
import com.sportsapp.application.goods.usecase.ExpirePendingGoodsOrdersUseCase
import com.sportsapp.application.goods.usecase.IsGoodsOrderExpiryEnabledUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * W1-11a — goods.expiry.enabled 런타임 플래그 분기(no-conditional-on-property 준수).
 * 빈 등록 자체는 항상 되고, 실행 시점에 [IsGoodsOrderExpiryEnabledUseCase]로 플래그를 조회해
 * 분기한다. 지표 카운터(goods_expiry_expired_total/goods_expiry_skipped_total/
 * goods_expiry_skipped_settled_total/goods_expiry_contended_total/
 * goods_expiry_chunk_failed_candidates_total)도 검증한다.
 */
class GoodsOrderExpirySchedulerTest : BehaviorSpec({

    Given("goods.expiry.enabled=true(플래그 ON)") {
        val useCase = mockk<ExpirePendingGoodsOrdersUseCase>()
        val isEnabledUseCase = mockk<IsGoodsOrderExpiryEnabledUseCase>()
        val meterRegistry = SimpleMeterRegistry()
        val scheduler = GoodsOrderExpiryScheduler(useCase, isEnabledUseCase, meterRegistry)
        every { isEnabledUseCase.execute() } returns true
        every { useCase.execute() } returns GoodsOrderExpiryResult(
            expiredCount = 3,
            skippedCount = 2,
            skippedSettledCount = 1,
            contendedCount = 1,
            chunkFailedCandidateCount = 1,
        )

        When("expirePendingGoodsOrders를 호출하면") {
            scheduler.expirePendingGoodsOrders()

            Then("ExpirePendingGoodsOrdersUseCase를 1회 호출한다") {
                verify(exactly = 1) { useCase.execute() }
            }

            Then("만료·건너뜀·settled 건너뜀·경합·청크실패 건수가 각각 카운터에 반영된다") {
                meterRegistry.counter("goods_expiry_expired_total").count() shouldBe 3.0
                meterRegistry.counter("goods_expiry_skipped_total").count() shouldBe 2.0
                meterRegistry.counter("goods_expiry_skipped_settled_total").count() shouldBe 1.0
                meterRegistry.counter("goods_expiry_contended_total").count() shouldBe 1.0
                meterRegistry.counter("goods_expiry_chunk_failed_candidates_total").count() shouldBe 1.0
            }
        }
    }

    Given("goods.expiry.enabled=false(플래그 OFF, 롤백 경로)") {
        val useCase = mockk<ExpirePendingGoodsOrdersUseCase>()
        val isEnabledUseCase = mockk<IsGoodsOrderExpiryEnabledUseCase>()
        val meterRegistry = SimpleMeterRegistry()
        val scheduler = GoodsOrderExpiryScheduler(useCase, isEnabledUseCase, meterRegistry)
        every { isEnabledUseCase.execute() } returns false

        When("expirePendingGoodsOrders를 호출하면") {
            scheduler.expirePendingGoodsOrders()

            Then("ExpirePendingGoodsOrdersUseCase를 호출하지 않고 아무 것도 하지 않는다") {
                verify(exactly = 0) { useCase.execute() }
            }
        }
    }
})
