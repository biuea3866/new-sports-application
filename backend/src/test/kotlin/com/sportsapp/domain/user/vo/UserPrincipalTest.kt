package com.sportsapp.domain.user.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class UserPrincipalTest : BehaviorSpec({

    Given("partnerAuthenticated 를 명시하지 않고 UserPrincipal 을 생성하면") {
        val principal = UserPrincipal(id = 1L, email = "user@example.com", roles = listOf("USER"))

        When("partnerAuthenticated 를 조회하면") {
            Then("기본값 false 를 반환한다 (기존 JWT 생성부 하위 호환)") {
                principal.partnerAuthenticated shouldBe false
            }
        }
    }

    Given("partnerAuthenticated=true 를 명시해 UserPrincipal 을 생성하면") {
        val principal = UserPrincipal(
            id = 10L,
            email = "partner-10@example.com",
            roles = listOf("GOODS_SELLER"),
            partnerAuthenticated = true,
        )

        When("partnerAuthenticated 를 조회하면") {
            Then("true 를 반환한다") {
                principal.partnerAuthenticated shouldBe true
            }
        }
    }
})
