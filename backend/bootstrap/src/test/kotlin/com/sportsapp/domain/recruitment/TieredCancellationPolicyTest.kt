package com.sportsapp.domain.recruitment

import com.sportsapp.domain.recruitment.policy.TieredCancellationPolicy
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime

class TieredCancellationPolicyTest : BehaviorSpec({

    val policy = TieredCancellationPolicy()

    Given("신청 마감까지 8일 남은 경우") {
        val deadline = ZonedDateTime.now().plusDays(8)

        Then("취소 수수료율은 0%다") {
            policy.feeRateFor(deadline).compareTo(BigDecimal.ZERO) shouldBe 0
        }
    }

    Given("신청 마감까지 정확히 7일 남은 경우") {
        val deadline = ZonedDateTime.now().plusDays(7)

        Then("취소 수수료율은 0%다") {
            policy.feeRateFor(deadline).compareTo(BigDecimal.ZERO) shouldBe 0
        }
    }

    Given("신청 마감까지 5일 남은 경우") {
        val deadline = ZonedDateTime.now().plusDays(5)

        Then("취소 수수료율은 5%다") {
            policy.feeRateFor(deadline).compareTo(BigDecimal("0.05")) shouldBe 0
        }
    }

    Given("신청 마감까지 정확히 3일 남은 경우") {
        val deadline = ZonedDateTime.now().plusDays(3)

        Then("취소 수수료율은 10%다") {
            policy.feeRateFor(deadline).compareTo(BigDecimal("0.10")) shouldBe 0
        }
    }

    Given("신청 마감까지 1일 남은 경우") {
        val deadline = ZonedDateTime.now().plusDays(1)

        Then("취소 수수료율은 10%다") {
            policy.feeRateFor(deadline).compareTo(BigDecimal("0.10")) shouldBe 0
        }
    }
})
