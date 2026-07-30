package com.sportsapp.presentation.goods.worker

import com.sportsapp.domain.goods.event.LimitedDropUnderRestoredEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry

private const val DROP_ID = 1L
private const val PRODUCT_ID = 10L
private const val RESTORED_COUNT = 3
private const val STALE_RESERVATION_COUNT = 4

class LimitedDropUnderRestoredEventWorkerTest : BehaviorSpec({

    Given("LimitedDropUnderRestoredEvent가 발행된 상황") {
        val meterRegistry = SimpleMeterRegistry()
        val worker = LimitedDropUnderRestoredEventWorker(meterRegistry)
        val event = LimitedDropUnderRestoredEvent(
            dropId = DROP_ID,
            productId = PRODUCT_ID,
            restoredCount = RESTORED_COUNT,
            staleReservationCount = STALE_RESERVATION_COUNT,
        )

        When("onUnderRestored를 호출하면") {
            worker.onUnderRestored(event)

            Then("[FIX-04] limited_drop_underrestored_total 카운터가 복원 건수만큼 증가한다") {
                meterRegistry.counter("limited_drop_underrestored_total").count() shouldBe RESTORED_COUNT.toDouble()
            }

            Then("[FIX-04] limited_drop_underrestored_stale_total 카운터가 고립 예약 후보 수만큼 증가한다") {
                meterRegistry.counter("limited_drop_underrestored_stale_total").count() shouldBe STALE_RESERVATION_COUNT.toDouble()
            }
        }
    }
})
