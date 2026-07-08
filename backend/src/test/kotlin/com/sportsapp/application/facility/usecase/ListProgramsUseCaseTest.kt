package com.sportsapp.application.facility.usecase

import com.sportsapp.domain.facility.entity.Program
import com.sportsapp.domain.facility.service.ProgramDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal

class ListProgramsUseCaseTest : BehaviorSpec({

    val programDomainService = mockk<ProgramDomainService>()
    val useCase = ListProgramsUseCase(programDomainService)

    Given("시설상품이 등록된 시설의 목록 조회") {
        val program = Program.create(
            facilityId = "FAC-01",
            ownerUserId = 1L,
            name = "그룹 클래스",
            description = null,
            price = BigDecimal.ZERO,
            capacity = 10,
            durationMinutes = 50,
        )
        every { programDomainService.findByFacility("FAC-01") } returns listOf(program)

        When("execute 를 호출하면") {
            val result = useCase.execute("FAC-01")

            Then("등록된 program 목록을 ProgramResponse 로 반환한다") {
                result shouldHaveSize 1
                result[0].name shouldBe "그룹 클래스"
            }
        }
    }

    Given("시설상품이 없는 시설의 목록 조회") {
        every { programDomainService.findByFacility("FAC-EMPTY") } returns emptyList()

        When("execute 를 호출하면") {
            val result = useCase.execute("FAC-EMPTY")

            Then("빈 목록을 반환한다") {
                result shouldHaveSize 0
            }
        }
    }
})
