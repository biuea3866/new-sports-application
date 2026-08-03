package com.sportsapp.application.user.usecase

import com.sportsapp.application.user.dto.ChangeMyNicknameCommand
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.UserDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ChangeMyNicknameUseCaseTest : BehaviorSpec({

    val userDomainService = mockk<UserDomainService>()
    val changeMyNicknameUseCase = ChangeMyNicknameUseCase(userDomainService)

    Given("마이페이지 닉네임 수정 요청") {
        val command = ChangeMyNicknameCommand(userId = 7L, nickname = "새로운닉네임")
        val changedUser = User.create("me@example.com", "hash", "새로운닉네임")
        every { userDomainService.changeNickname(7L, "새로운닉네임") } returns changedUser

        When("execute 를 호출하면") {
            val user = changeMyNicknameUseCase.execute(command)

            Then("DomainService 만 호출하고 수정된 사용자를 반환한다") {
                user.nickname shouldBe "새로운닉네임"
                verify(exactly = 1) { userDomainService.changeNickname(7L, "새로운닉네임") }
            }
        }
    }
})
