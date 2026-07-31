package com.sportsapp.domain.partner.exception

import com.sportsapp.domain.common.ErrorStatus

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PartnerNotFoundExceptionTest : BehaviorSpec({

    Given("존재하지 않는 partnerId로 PartnerNotFoundException을 생성하면") {
        val exception = PartnerNotFoundException(999L)

        Then("status는 NOT_FOUND(404)로 매핑된다") {
            exception.status shouldBe ErrorStatus.NOT_FOUND
        }

        Then("메시지에 partnerId가 포함된다") {
            exception.message shouldBe "Partner(id=999) not found"
        }
    }
})
