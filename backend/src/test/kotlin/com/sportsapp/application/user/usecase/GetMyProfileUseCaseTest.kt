package com.sportsapp.application.user.usecase

import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.application.user.dto.GetMyProfileCommand
import com.sportsapp.domain.user.entity.UserStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class GetMyProfileUseCaseTest : BehaviorSpec({

    val userDomainService = mockk<UserDomainService>()
    val useCase = GetMyProfileUseCase(userDomainService)

    Given("[U-01] 인증된 userId로 프로필 조회 시") {
        val userId = 1L
        val user = mockk<User> {
            every { id } returns userId
            every { email } returns "test@example.com"
            every { status } returns UserStatus.ACTIVE
            every { createdAt } returns java.time.ZonedDateTime.parse("2024-01-01T00:00:00Z")
        }
        every { userDomainService.findById(userId) } returns user

        When("execute를 호출하면") {
            val result = useCase.execute(GetMyProfileCommand(userId = userId))

            Then("[U-01] UserProfile이 올바른 필드를 담아 반환된다") {
                result.id shouldBe userId
                result.email shouldBe "test@example.com"
                result.status shouldBe UserStatus.ACTIVE
            }

            Then("[U-01] UserDomainService만 호출하고 Repository를 직접 참조하지 않는다") {
                verify(exactly = 1) { userDomainService.findById(userId) }
            }
        }
    }
})
