package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.CreateProgramSessionCommand
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.service.SlotDomainService
import com.sportsapp.domain.facility.entity.Program
import com.sportsapp.domain.facility.exception.UnauthorizedProgramAccessException
import com.sportsapp.domain.facility.service.ProgramDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class CreateProgramSessionUseCaseTest : BehaviorSpec({

    val programDomainService = mockk<ProgramDomainService>()
    val slotDomainService = mockk<SlotDomainService>()
    val useCase = CreateProgramSessionUseCase(programDomainService, slotDomainService)

    Given("소유자가 program 회차(슬롯) 생성을 요청하는 경우") {
        // Program.id 는 @GeneratedValue(IDENTITY) + val 이라 실제 저장 없이는 값을 바꿀 수 없다
        // (CreateCommunityUseCaseTest 의 relaxed mock 관례와 동일 — "저장되어 id=10 으로 확정된 program"을 흉내낸다).
        val program = mockk<Program>(relaxed = true) {
            every { id } returns 10L
            every { facilityId } returns "FAC-01"
            every { capacity } returns 1
        }
        val date = ZonedDateTime.now()
        every { programDomainService.getOwnedProgram(1L, 10L) } returns program
        val slot = Slot.create(
            facilityId = "FAC-01",
            date = date,
            timeRange = "09:00-10:00",
            capacity = 1,
            ownerId = 1L,
            programId = 10L,
        )
        every {
            slotDomainService.createSlot(
                ownerId = 1L,
                facilityId = "FAC-01",
                date = date,
                timeRange = "09:00-10:00",
                capacity = 1,
                programId = 10L,
            )
        } returns slot

        When("execute 를 호출하면") {
            val result = useCase.execute(
                CreateProgramSessionCommand(requesterId = 1L, programId = 10L, date = date, timeRange = "09:00-10:00"),
            )

            Then("programId 를 가진 회차 슬롯이 생성된다") {
                result.programId shouldBe 10L
                result.facilityId shouldBe "FAC-01"
                verify {
                    slotDomainService.createSlot(
                        ownerId = 1L,
                        facilityId = "FAC-01",
                        date = date,
                        timeRange = "09:00-10:00",
                        capacity = 1,
                        programId = 10L,
                    )
                }
            }
        }
    }

    Given("소유자가 아닌 사용자가 program 회차 생성을 요청하는 경우") {
        every {
            programDomainService.getOwnedProgram(99L, 10L)
        } throws UnauthorizedProgramAccessException(10L, 99L)

        When("execute 를 호출하면") {
            Then("UnauthorizedProgramAccessException 이 발생한다") {
                shouldThrow<UnauthorizedProgramAccessException> {
                    useCase.execute(
                        CreateProgramSessionCommand(
                            requesterId = 99L,
                            programId = 10L,
                            date = ZonedDateTime.now(),
                            timeRange = "09:00-10:00",
                        ),
                    )
                }
            }
        }
    }
})
