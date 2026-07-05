package com.sportsapp.domain.partner.entity
import com.sportsapp.domain.partner.exception.PartnerSuspendedException

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PartnerTest : BehaviorSpec({

    fun createActivePartner(): Partner = Partner.create(
        name = "test-partner",
        linkedUserId = 1L,
    )

    Given("새로 생성된 Partner") {
        val partner = createActivePartner()

        Then("status=ACTIVE로 생성된다") {
            partner.status shouldBe PartnerStatus.ACTIVE
        }
    }

    Given("ACTIVE 상태의 Partner에 deactivate()를 호출하면") {
        val partner = createActivePartner()
        partner.deactivate()

        Then("status=SUSPENDED로 전이된다") {
            partner.status shouldBe PartnerStatus.SUSPENDED
        }
    }

    Given("ACTIVE 상태의 Partner에 validateActive()를 호출하면") {
        val partner = createActivePartner()

        Then("예외가 발생하지 않는다") {
            partner.validateActive()
        }
    }

    Given("SUSPENDED 상태의 Partner에 activate()를 호출하면") {
        val partner = createActivePartner()
        partner.deactivate()
        partner.activate()

        Then("status=ACTIVE로 전이된다") {
            partner.status shouldBe PartnerStatus.ACTIVE
        }
    }

    Given("SUSPENDED 상태의 Partner에 validateActive()를 호출하면") {
        val partner = createActivePartner()
        partner.deactivate()

        Then("PartnerSuspendedException이 발생한다") {
            shouldThrow<PartnerSuspendedException> {
                partner.validateActive()
            }
        }
    }

    Given("이미 ACTIVE 상태인 Partner에 activate()를 재호출하면") {
        val partner = createActivePartner()
        partner.activate()

        Then("상태가 ACTIVE로 유지된다") {
            partner.status shouldBe PartnerStatus.ACTIVE
        }
    }

    Given("이미 SUSPENDED 상태인 Partner에 deactivate()를 재호출하면") {
        val partner = createActivePartner()
        partner.deactivate()
        partner.deactivate()

        Then("상태가 SUSPENDED로 유지된다") {
            partner.status shouldBe PartnerStatus.SUSPENDED
        }
    }

    Given("빈 문자열 name으로 Partner를 생성하면") {
        Then("IllegalArgumentException이 발생한다") {
            shouldThrow<IllegalArgumentException> {
                Partner.create(name = "", linkedUserId = 1L)
            }
        }
    }
})
