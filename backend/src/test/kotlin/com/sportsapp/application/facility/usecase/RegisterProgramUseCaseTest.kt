package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.RegisterProgramCommand
import com.sportsapp.domain.facility.entity.Program
import com.sportsapp.domain.facility.exception.UnauthorizedFacilityAccessException
import com.sportsapp.domain.facility.service.ProgramDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class RegisterProgramUseCaseTest : BehaviorSpec({

    val programDomainService = mockk<ProgramDomainService>()
    val useCase = RegisterProgramUseCase(programDomainService)

    Given("소유자가 PT 상품(정원1·60분) 등록 커맨드") {
        val program = Program.create(
            facilityId = "FAC-01",
            ownerUserId = 1L,
            name = "1:1 PT",
            description = "개인 트레이닝",
            price = BigDecimal("50000"),
            capacity = 1,
            durationMinutes = 60,
        )
        every {
            programDomainService.register(
                facilityId = "FAC-01",
                ownerUserId = 1L,
                name = "1:1 PT",
                description = "개인 트레이닝",
                price = BigDecimal("50000"),
                capacity = 1,
                durationMinutes = 60,
            )
        } returns program

        When("execute 를 호출하면") {
            val command = RegisterProgramCommand(
                facilityId = "FAC-01",
                ownerUserId = 1L,
                name = "1:1 PT",
                description = "개인 트레이닝",
                price = BigDecimal("50000"),
                capacity = 1,
                durationMinutes = 60,
            )
            val result = useCase.execute(command)

            Then("ProgramResponse 가 반환된다") {
                result.facilityId shouldBe "FAC-01"
                result.capacity shouldBe 1
                result.durationMinutes shouldBe 60
                verify { programDomainService.register(any(), any(), any(), any(), any(), any(), any()) }
            }
        }
    }

    Given("소유자가 아닌 사용자의 등록 커맨드") {
        every {
            programDomainService.register(
                facilityId = "FAC-01",
                ownerUserId = 99L,
                name = "1:1 PT",
                description = null,
                price = BigDecimal.ZERO,
                capacity = 1,
                durationMinutes = 60,
            )
        } throws UnauthorizedFacilityAccessException("FAC-01")

        When("execute 를 호출하면") {
            Then("UnauthorizedFacilityAccessException 이 발생한다") {
                shouldThrow<UnauthorizedFacilityAccessException> {
                    useCase.execute(
                        RegisterProgramCommand(
                            facilityId = "FAC-01",
                            ownerUserId = 99L,
                            name = "1:1 PT",
                            description = null,
                            price = BigDecimal.ZERO,
                            capacity = 1,
                            durationMinutes = 60,
                        ),
                    )
                }
            }
        }
    }
})
