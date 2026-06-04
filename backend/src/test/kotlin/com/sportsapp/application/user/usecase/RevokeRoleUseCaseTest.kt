package com.sportsapp.application.user.usecase

import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.application.user.dto.RevokeRoleCommand
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class RevokeRoleUseCaseTest : BehaviorSpec({

    val userDomainService = mockk<UserDomainService>()
    val revokeRoleUseCase = RevokeRoleUseCase(userDomainService)

    Given("ADMIN이 특정 사용자의 FACILITY_OWNER Role을 회수하는 경우") {
        val command = RevokeRoleCommand(adminId = 1L, userId = 7L, roleName = "FACILITY_OWNER")

        When("execute 호출 시") {
            justRun { userDomainService.revokeRole(1L, 7L, "FACILITY_OWNER") }

            Then("[Unit] UserDomainService.revokeRole 이 정확한 인자로 호출된다") {
                revokeRoleUseCase.execute(command)
                verify(exactly = 1) {
                    userDomainService.revokeRole(
                        adminId = 1L,
                        userId = 7L,
                        roleName = "FACILITY_OWNER",
                    )
                }
            }
        }
    }
})
