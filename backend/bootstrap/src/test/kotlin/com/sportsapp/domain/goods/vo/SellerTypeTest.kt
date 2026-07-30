package com.sportsapp.domain.goods.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class SellerTypeTest : BehaviorSpec({

    Given("파트너 API Key 인증을 경유한 등록 요청") {
        When("SellerType.fromPartnerAuthenticated(true)를 호출하면") {
            Then("B2B를 반환한다") {
                SellerType.fromPartnerAuthenticated(true) shouldBe SellerType.B2B
            }
        }
    }

    Given("일반 JWT 인증(또는 미인증)으로 온 등록 요청") {
        When("SellerType.fromPartnerAuthenticated(false)를 호출하면") {
            Then("B2C를 반환한다") {
                SellerType.fromPartnerAuthenticated(false) shouldBe SellerType.B2C
            }
        }
    }
})
