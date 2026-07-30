package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.entity.Message
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.service.MessageDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class ListMessagesUseCaseTest : BehaviorSpec({

    val messageDomainService = mockk<MessageDomainService>()
    val listMessagesUseCase = ListMessagesUseCase(messageDomainService)

    Given("참여자인 사용자가 커서 없이 메시지 목록을 조회할 때") {
        val baseTime = ZonedDateTime.now()
        val room = mockk<Room>()
        every { room.id } returns 1L
        val messages = (1..5).map { index ->
            mockk<Message>().also { m ->
                every { m.id } returns index.toLong()
                every { m.room } returns room
                every { m.userId } returns 10L
                every { m.content } returns "메시지$index"
                every { m.createdAt } returns baseTime.minusSeconds(index.toLong())
            }
        }
        every { messageDomainService.listMessages(1L, 10L, null) } returns messages

        When("cursor=null 로 execute 를 호출하면") {
            val result = listMessagesUseCase.execute(1L, 10L, null)

            Then("5건 메시지가 반환된다") {
                result shouldHaveSize 5
            }
        }
    }

    Given("참여자인 사용자가 30건 정확히 채워진 메시지 목록을 조회할 때") {
        val baseTime = ZonedDateTime.now()
        val room = mockk<Room>()
        every { room.id } returns 1L
        val messages = (1..30).map { index ->
            mockk<Message>().also { m ->
                every { m.id } returns index.toLong()
                every { m.room } returns room
                every { m.userId } returns 10L
                every { m.content } returns "메시지$index"
                every { m.createdAt } returns baseTime.minusSeconds(index.toLong())
            }
        }
        every { messageDomainService.listMessages(1L, 10L, "2026-01-01T00:00:00Z") } returns messages

        When("cursor 값과 함께 execute 를 호출하면") {
            val result = listMessagesUseCase.execute(1L, 10L, "2026-01-01T00:00:00Z")

            Then("30건 메시지가 반환된다") {
                result shouldHaveSize 30
            }
        }
    }

    Given("비참여자가 메시지 목록을 조회할 때") {
        every { messageDomainService.listMessages(1L, 99L, null) } throws NotRoomParticipantException(99L, 1L)

        When("execute 를 호출하면") {
            Then("NotRoomParticipantException 이 발생한다") {
                shouldThrow<NotRoomParticipantException> {
                    listMessagesUseCase.execute(1L, 99L, null)
                }
            }
        }
    }
})
