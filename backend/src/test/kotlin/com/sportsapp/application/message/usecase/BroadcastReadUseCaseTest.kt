package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.BroadcastReadCommand
import com.sportsapp.domain.message.service.ReadCursorDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class BroadcastReadUseCaseTest : BehaviorSpec({

    val readCursorDomainService = mockk<ReadCursorDomainService>(relaxed = true)
    val broadcastReadUseCase = BroadcastReadUseCase(readCursorDomainService)

    Given("커밋된 읽음 커서 브로드캐스트 명령이 주어지면") {
        val command = BroadcastReadCommand(roomId = 1L, userId = 10L, lastReadMessageId = 100L)
        every {
            readCursorDomainService.broadcastRead(roomId = 1L, userId = 10L, lastReadMessageId = 100L)
        } returns Unit

        When("execute 를 호출하면") {
            broadcastReadUseCase.execute(command)

            Then("ReadCursorDomainService.broadcastRead 가 위임 호출된다") {
                verify(exactly = 1) {
                    readCursorDomainService.broadcastRead(roomId = 1L, userId = 10L, lastReadMessageId = 100L)
                }
            }
        }
    }
})
