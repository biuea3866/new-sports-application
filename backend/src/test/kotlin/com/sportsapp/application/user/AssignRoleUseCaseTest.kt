package com.sportsapp.application.user

import com.sportsapp.domain.user.UserDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class AssignRoleUseCaseTest : BehaviorSpec({

    val userDomainService = mockk<UserDomainService>()
    val assignRoleUseCase = AssignRoleUseCase(userDomainService)

    Given("ADMIN이 특정 사용자에게 FACILITY_OWNER Role을 부여하는 경우") {
        val command = AssignRoleCommand(adminId = 1L, userId = 7L, roleName = "FACILITY_OWNER")

        When("execute 호출 시") {
            justRun { userDomainService.assignRole(1L, 7L, "FACILITY_OWNER") }

            Then("[Unit] UserDomainService.assignRole 이 정확한 인자로 호출된다") {
                assignRoleUseCase.execute(command)
                verify(exactly = 1) {
                    userDomainService.assignRole(
                        adminId = 1L,
                        userId = 7L,
                        roleName = "FACILITY_OWNER",
                    )
                }
            }
        }
    }
})
