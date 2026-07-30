package com.sportsapp.presentation.goods.worker

import com.sportsapp.domain.goods.event.LimitedDropOversoldEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry

private const val DROP_ID = 1L
private const val PRODUCT_ID = 10L
private const val DETECTED_QUANTITY = 5

class LimitedDropOversoldEventWorkerTest : BehaviorSpec({

    Given("LimitedDropOversoldEvent가 발행된 상황") {
        val meterRegistry = SimpleMeterRegistry()
        val worker = LimitedDropOversoldEventWorker(meterRegistry)
        val event = LimitedDropOversoldEvent(
            dropId = DROP_ID,
            productId = PRODUCT_ID,
            detectedQuantity = DETECTED_QUANTITY,
        )

        When("onOversoldDetected를 호출하면") {
            worker.onOversoldDetected(event)

            Then("limited_drop_oversell_total 카운터가 증가한다") {
                meterRegistry.counter("limited_drop_oversell_total").count() shouldBe 1.0
            }
        }
    }
})
