package com.sportsapp.application.user.usecase

import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.application.user.dto.RegisterUserCommand
import com.sportsapp.domain.user.entity.UserStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class RegisterUserUseCaseTest : BehaviorSpec({

    val userDomainService = mockk<UserDomainService>()
    val registerUserUseCase = RegisterUserUseCase(userDomainService)

    Given("정상 가입 요청") {
        val command = RegisterUserCommand(email = "new@example.com", rawPassword = "password1234")
        val savedUser = User(
            email = "new@example.com",
            passwordHash = "\$2a\$10\$hashed",
            status = UserStatus.ACTIVE,
        )

        every { userDomainService.register(command.email, command.rawPassword) } returns savedUser

        When("execute 를 호출하면") {
            val response = registerUserUseCase.execute(command)

            Then("[U-01] DomainService 만 호출하고 응답을 반환한다") {
                response.email shouldBe "new@example.com"
                verify(exactly = 1) { userDomainService.register(command.email, command.rawPassword) }
            }
        }
    }
})
