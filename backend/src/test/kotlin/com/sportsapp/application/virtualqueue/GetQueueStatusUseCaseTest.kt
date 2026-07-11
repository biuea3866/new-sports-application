package com.sportsapp.application.virtualqueue

import com.sportsapp.application.virtualqueue.dto.GetQueueStatusCommand
import com.sportsapp.application.virtualqueue.usecase.GetQueueStatusUseCase
import com.sportsapp.domain.virtualqueue.exception.QueueEntryNotFoundException
import com.sportsapp.domain.virtualqueue.service.VirtualQueueDomainService
import com.sportsapp.domain.virtualqueue.vo.EntryToken
import com.sportsapp.domain.virtualqueue.vo.QueuePosition
import com.sportsapp.domain.virtualqueue.vo.QueueStatus
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

private const val USER_ID = 200L

/**
 * `GetQueueStatusUseCase` — `VirtualQueueDomainService.status` 위임(execute ≤10줄, 폴링/heartbeat
 * 겸용)과 QueueStatus → QueueEntryResponse 변환을 검증한다
 */
class GetQueueStatusUseCaseTest : BehaviorSpec({

    val target = QueueTarget(QueueTargetType.TICKETING_EVENT, 5L)
    val command = GetQueueStatusCommand(type = QueueTargetType.TICKETING_EVENT, targetId = 5L, userId = USER_ID)

    Given("admission 판정 통과로 ADMITTED가 반환되는 상황에서") {
        val virtualQueueDomainService = mockk<VirtualQueueDomainService>()
        val useCase = GetQueueStatusUseCase(virtualQueueDomainService)
        val entryToken = EntryToken(raw = "admitted-token", expiresAt = ZonedDateTime.now().plusMinutes(5))
        every { virtualQueueDomainService.status(target, USER_ID) } returns QueueStatus.admitted(entryToken)

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("DomainService.status에 위임하고 entryToken을 포함한 ADMITTED를 반환한다") {
                result.status shouldBe "ADMITTED"
                result.entryToken shouldBe "admitted-token"
                result.position shouldBe null
                verify(exactly = 1) { virtualQueueDomainService.status(target, USER_ID) }
            }
        }
    }

    Given("아직 대기 중(WAITING)인 상황에서") {
        val virtualQueueDomainService = mockk<VirtualQueueDomainService>()
        val useCase = GetQueueStatusUseCase(virtualQueueDomainService)
        val position = QueuePosition.of(rank = 9, seq = 10, admittedCount = 0, batchSize = 10, tickSeconds = 2)
        every { virtualQueueDomainService.status(target, USER_ID) } returns QueueStatus.waiting(position)

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("aheadCount·etaSeconds를 포함한 WAITING을 반환한다") {
                result.status shouldBe "WAITING"
                result.aheadCount shouldBe 9L
                result.etaSeconds shouldBe position.etaSeconds
                result.entryToken shouldBe null
            }
        }
    }

    Given("큐에 존재하지 않는 사용자(QueueEntryNotFoundException)인 상황에서") {
        val virtualQueueDomainService = mockk<VirtualQueueDomainService>()
        val useCase = GetQueueStatusUseCase(virtualQueueDomainService)
        every { virtualQueueDomainService.status(target, USER_ID) } throws QueueEntryNotFoundException(target, USER_ID)

        When("execute를 호출하면") {
            Then("예외를 그대로 전파한다 (FE 계약상 404 매핑)") {
                shouldThrow<QueueEntryNotFoundException> { useCase.execute(command) }
            }
        }
    }
})
