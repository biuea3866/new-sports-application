package com.sportsapp.domain.booking

import com.sportsapp.domain.booking.entity.SlotStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class SlotStatusTest : BehaviorSpec({

    Given("OPEN 상태") {
        Then("CLOSED로는 전이 가능하다") {
            SlotStatus.OPEN.canTransitTo(SlotStatus.CLOSED) shouldBe true
        }
        Then("OPEN 자신으로는 전이 불가능하다") {
            SlotStatus.OPEN.canTransitTo(SlotStatus.OPEN) shouldBe false
        }
    }

    Given("CLOSED 상태") {
        Then("OPEN으로는 전이 가능하다") {
            SlotStatus.CLOSED.canTransitTo(SlotStatus.OPEN) shouldBe true
        }
        Then("CLOSED 자신으로는 전이 불가능하다") {
            SlotStatus.CLOSED.canTransitTo(SlotStatus.CLOSED) shouldBe false
        }
    }
})
