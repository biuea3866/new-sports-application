package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.entity.Message
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.service.MessageBackfillDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.every
import io.mockk.mockk

class BackfillMessagesUseCaseTest : BehaviorSpec({

    val messageBackfillDomainService = mockk<MessageBackfillDomainService>()
    val backfillMessagesUseCase = BackfillMessagesUseCase(messageBackfillDomainService)

    Given("끊긴 구간에 메시지가 있을 때") {
        val room = Room.createDirect()
        val messages = listOf(
            Message.create(room, 1L, "메시지1"),
            Message.create(room, 1L, "메시지2"),
        )
        every { messageBackfillDomainService.backfill(1L, 10L, 120L) } returns messages

        When("execute(roomId=1, userId=10, afterMessageId=120) 을 호출하면") {
            val result = backfillMessagesUseCase.execute(roomId = 1L, userId = 10L, afterMessageId = 120L)

            Then("끊긴 구간 메시지 목록을 반환한다") {
                result shouldHaveSize 2
            }
        }
    }

    Given("끊긴 구간이 없을 때") {
        every { messageBackfillDomainService.backfill(2L, 20L, 999L) } returns emptyList()

        When("execute(roomId=2, userId=20, afterMessageId=999) 를 호출하면") {
            val result = backfillMessagesUseCase.execute(roomId = 2L, userId = 20L, afterMessageId = 999L)

            Then("빈 목록을 반환한다") {
                result.shouldBeEmpty()
            }
        }
    }

    Given("방 참여자가 아닌 사용자") {
        every { messageBackfillDomainService.backfill(3L, 99L, 10L) } throws NotRoomParticipantException(99L, 3L)

        When("execute 를 호출하면") {
            Then("NotRoomParticipantException 이 그대로 전파된다") {
                shouldThrow<NotRoomParticipantException> {
                    backfillMessagesUseCase.execute(roomId = 3L, userId = 99L, afterMessageId = 10L)
                }
            }
        }
    }
})
