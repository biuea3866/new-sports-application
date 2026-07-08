package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.service.ReadCursorDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder

class MarkReadUseCaseTest : BehaviorSpec({

    val readCursorDomainService = mockk<ReadCursorDomainService>()
    val markReadUseCase = MarkReadUseCase(readCursorDomainService)

    Given("참여자가 읽음 커서를 갱신할 때") {
        val room = Room.createDirect()
        val participant = RoomParticipant.create(room, 10L)
        every { readCursorDomainService.markRead(1L, 10L, 100L) } returns participant
        every { readCursorDomainService.unreadCount(1L, 10L) } returns 0L

        When("execute(roomId=1, userId=10, lastReadMessageId=100) 를 호출하면") {
            val result = markReadUseCase.execute(roomId = 1L, userId = 10L, lastReadMessageId = 100L)

            Then("커서를 갱신한 뒤 안읽은 수를 조회해 RoomUnreadResponse 를 반환한다") {
                result.roomId shouldBe 1L
                result.unreadCount shouldBe 0L
                verifyOrder {
                    readCursorDomainService.markRead(1L, 10L, 100L)
                    readCursorDomainService.unreadCount(1L, 10L)
                }
            }
        }
    }

    Given("비참여자가 읽음 처리를 요청할 때") {
        every { readCursorDomainService.markRead(2L, 99L, 5L) } throws NotRoomParticipantException(99L, 2L)

        When("execute 를 호출하면") {
            Then("NotRoomParticipantException 이 발생한다") {
                shouldThrow<NotRoomParticipantException> {
                    markReadUseCase.execute(roomId = 2L, userId = 99L, lastReadMessageId = 5L)
                }
            }
        }
    }
})
