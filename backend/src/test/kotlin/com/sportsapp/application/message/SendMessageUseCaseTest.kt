package com.sportsapp.application.message

import com.sportsapp.domain.message.Message
import com.sportsapp.domain.message.MessageDomainService
import com.sportsapp.domain.message.NotRoomParticipantException
import com.sportsapp.domain.message.Room
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class SendMessageUseCaseTest : BehaviorSpec({

    val messageDomainService = mockk<MessageDomainService>()
    val sendMessageUseCase = SendMessageUseCase(messageDomainService)

    Given("참여자인 사용자가 메시지를 작성할 때") {
        val message = mockk<Message>()
        val room = mockk<Room>()
        val sentAt = ZonedDateTime.now()
        every { message.id } returns 1L
        every { message.room } returns room
        every { room.id } returns 1L
        every { message.userId } returns 10L
        every { message.content } returns "안녕하세요"
        every { message.createdAt } returns sentAt
        every { messageDomainService.sendMessage(1L, 10L, "안녕하세요") } returns message

        When("execute 를 호출하면") {
            val command = SendMessageCommand(roomId = 1L, senderId = 10L, content = "안녕하세요")
            val result = sendMessageUseCase.execute(command)

            Then("MessageResponse 가 반환된다") {
                result.roomId shouldBe 1L
                result.senderId shouldBe 10L
                result.content shouldBe "안녕하세요"
                result.sentAt shouldBe sentAt
                verify { messageDomainService.sendMessage(1L, 10L, "안녕하세요") }
            }
        }
    }

    Given("비참여자가 메시지를 작성할 때") {
        every { messageDomainService.sendMessage(1L, 99L, "테스트") } throws NotRoomParticipantException(99L, 1L)

        When("execute 를 호출하면") {
            Then("NotRoomParticipantException 이 발생한다") {
                shouldThrow<NotRoomParticipantException> {
                    sendMessageUseCase.execute(SendMessageCommand(roomId = 1L, senderId = 99L, content = "테스트"))
                }
            }
        }
    }
})
