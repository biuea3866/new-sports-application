package com.sportsapp.application.message.usecase

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.service.RoomParticipationQueryService
import com.sportsapp.domain.message.vo.ParticipantType
import com.sportsapp.domain.message.vo.RoomParticipationView
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class GetMyRoomParticipationUseCaseTest : BehaviorSpec({

    val roomParticipationQueryService = mockk<RoomParticipationQueryService>()
    val getMyRoomParticipationUseCase = GetMyRoomParticipationUseCase(roomParticipationQueryService)

    Given("정회원으로 참여 중인 방") {
        val view = RoomParticipationView(
            roomId = 10L,
            participantType = ParticipantType.MEMBER,
            canSpeak = true,
            expiresAt = null,
            isHost = true,
        )
        every { roomParticipationQueryService.getMyParticipation(10L, 1L) } returns view

        When("execute(roomId=10, userId=1) 를 호출하면") {
            val result = getMyRoomParticipationUseCase.execute(10L, 1L)

            Then("RoomParticipationQueryService 결과가 그대로 반환된다") {
                result shouldBe view
            }
        }
    }

    Given("존재하지 않는 방") {
        every { roomParticipationQueryService.getMyParticipation(999L, 1L) } throws
            ResourceNotFoundException("Room", 999L)

        When("execute(roomId=999, userId=1) 를 호출하면") {
            Then("ResourceNotFoundException 이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    getMyRoomParticipationUseCase.execute(999L, 1L)
                }
            }
        }
    }

    Given("참여하지 않은 방") {
        every { roomParticipationQueryService.getMyParticipation(10L, 99L) } throws
            NotRoomParticipantException(99L, 10L)

        When("execute(roomId=10, userId=99) 를 호출하면") {
            Then("NotRoomParticipantException 이 발생한다") {
                shouldThrow<NotRoomParticipantException> {
                    getMyRoomParticipationUseCase.execute(10L, 99L)
                }
            }
        }
    }
})
