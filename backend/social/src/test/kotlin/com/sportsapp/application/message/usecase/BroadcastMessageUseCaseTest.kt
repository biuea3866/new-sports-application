package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.BroadcastMessageCommand
import com.sportsapp.domain.message.service.MessageDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class BroadcastMessageUseCaseTest : BehaviorSpec({

    val messageDomainService = mockk<MessageDomainService>(relaxed = true)
    val broadcastMessageUseCase = BroadcastMessageUseCase(messageDomainService)

    Given("커밋된 메시지 브로드캐스트 명령이 주어지면") {
        val sentAt = ZonedDateTime.now()
        val command = BroadcastMessageCommand(
            roomId = 1L,
            messageId = 7L,
            senderId = 10L,
            content = "실시간 안녕",
            sentAt = sentAt,
        )
        every {
            messageDomainService.broadcastMessage(
                roomId = 1L,
                messageId = 7L,
                senderId = 10L,
                content = "실시간 안녕",
                sentAt = sentAt,
            )
        } returns Unit

        When("execute 를 호출하면") {
            broadcastMessageUseCase.execute(command)

            Then("MessageDomainService.broadcastMessage 가 위임 호출된다") {
                verify(exactly = 1) {
                    messageDomainService.broadcastMessage(
                        roomId = 1L,
                        messageId = 7L,
                        senderId = 10L,
                        content = "실시간 안녕",
                        sentAt = sentAt,
                    )
                }
            }
        }
    }
})
