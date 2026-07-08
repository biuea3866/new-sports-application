package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.service.MessageDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class DeleteRoomUseCaseTest : BehaviorSpec({

    val messageDomainService = mockk<MessageDomainService>()
    val deleteRoomUseCase = DeleteRoomUseCase(messageDomainService)

    Given("본인이 참가자인 룸") {
        justRun { messageDomainService.leaveRoom(10L, 1L) }

        When("execute 를 호출하면") {
            deleteRoomUseCase.execute(roomId = 10L, userId = 1L)

            Then("leaveRoom 이 호출된다") {
                verify { messageDomainService.leaveRoom(10L, 1L) }
            }
        }
    }

    Given("본인이 참가자가 아닌 룸") {
        every { messageDomainService.leaveRoom(20L, 99L) } throws NotRoomParticipantException(
            userId = 99L,
            roomId = 20L,
        )

        When("execute 를 호출하면") {
            Then("[U-02] NotRoomParticipantException 이 발생한다") {
                shouldThrow<NotRoomParticipantException> {
                    deleteRoomUseCase.execute(roomId = 20L, userId = 99L)
                }
            }
        }
    }

    Given("마지막 참가자가 탈퇴하는 룸") {
        justRun { messageDomainService.leaveRoom(30L, 1L) }

        When("execute 를 호출하면 (내부에서 마지막 1인 탈퇴 처리)") {
            deleteRoomUseCase.execute(roomId = 30L, userId = 1L)

            Then("[U-03] leaveRoom 이 호출되고 DomainService 가 Room+Message 삭제를 담당한다") {
                verify { messageDomainService.leaveRoom(30L, 1L) }
            }
        }
    }
})
