package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.BroadcastTypingCommand
import com.sportsapp.domain.message.service.MessageDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class SendTypingUseCaseTest : BehaviorSpec({

    val messageDomainService = mockk<MessageDomainService>(relaxed = true)
    val sendTypingUseCase = SendTypingUseCase(messageDomainService)

    Given("참여자가 타이핑 신호를 보낼 때") {
        val command = BroadcastTypingCommand(roomId = 2L, userId = 20L, typing = true)
        every {
            messageDomainService.broadcastTyping(roomId = 2L, userId = 20L, typing = true)
        } returns Unit

        When("execute 를 호출하면") {
            sendTypingUseCase.execute(command)

            Then("MessageDomainService.broadcastTyping 이 위임 호출된다") {
                verify(exactly = 1) {
                    messageDomainService.broadcastTyping(roomId = 2L, userId = 20L, typing = true)
                }
            }
        }
    }
})
