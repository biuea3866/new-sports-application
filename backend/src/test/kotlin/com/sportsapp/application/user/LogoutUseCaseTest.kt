package com.sportsapp.application.user

import com.sportsapp.domain.user.AuthDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class LogoutUseCaseTest : BehaviorSpec({

    val authDomainService = mockk<AuthDomainService>()
    val logoutUseCase = LogoutUseCase(authDomainService)

    Given("유효한 LogoutCommand 가 주어진 경우") {
        val command = LogoutCommand(accessToken = "valid.access.token", userId = 1L)

        every { authDomainService.logout(command.accessToken, command.userId) } returns Unit

        When("[U-01] execute 를 호출하면") {
            logoutUseCase.execute(command)

            Then("AuthDomainService.logout 만 호출한다") {
                verify(exactly = 1) { authDomainService.logout(command.accessToken, command.userId) }
            }
        }
    }
})
