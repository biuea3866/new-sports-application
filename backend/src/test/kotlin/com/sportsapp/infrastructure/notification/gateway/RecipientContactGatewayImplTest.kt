package com.sportsapp.infrastructure.notification.gateway

import com.sportsapp.domain.user.service.UserDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * RecipientContactGatewayImpl — notification이 user 컨텍스트를 직접 읽지 않고
 * 공급자 UserDomainService.findEmailBy를 경유하는지 검증한다. Repository가 아니라
 * DomainService를 모킹하는 것이 이 전환의 본질이다.
 */
class RecipientContactGatewayImplTest : BehaviorSpec({

    val userDomainService = mockk<UserDomainService>()
    val gateway = RecipientContactGatewayImpl(userDomainService)

    Given("존재하는 사용자 ID로 이메일 조회를 요청하면") {
        every { userDomainService.findEmailBy(10L) } returns "test@example.com"

        When("emailOf를 호출하면") {
            val email = gateway.emailOf(10L)

            Then("공급자 DomainService가 반환한 이메일을 그대로 반환한다") {
                email shouldBe "test@example.com"
                verify(exactly = 1) { userDomainService.findEmailBy(10L) }
            }
        }
    }

    Given("존재하지 않는 사용자 ID로 이메일 조회를 요청하면") {
        every { userDomainService.findEmailBy(999L) } returns null

        When("emailOf를 호출하면") {
            val email = gateway.emailOf(999L)

            Then("예외 없이 null을 반환한다") {
                email shouldBe null
            }
        }
    }
})
