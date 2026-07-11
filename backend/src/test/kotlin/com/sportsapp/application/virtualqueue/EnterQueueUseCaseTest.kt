package com.sportsapp.application.virtualqueue

import com.sportsapp.application.virtualqueue.dto.EnterQueueCommand
import com.sportsapp.application.virtualqueue.usecase.EnterQueueUseCase
import com.sportsapp.domain.virtualqueue.exception.QueueFullException
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

private const val USER_ID = 100L

/**
 * `EnterQueueUseCase` — `VirtualQueueDomainService.enter` 위임(execute ≤10줄)과
 * QueueStatus → QueueEntryResponse 변환을 검증한다
 */
class EnterQueueUseCaseTest : BehaviorSpec({

    val target = QueueTarget(QueueTargetType.LIMITED_DROP, 1L)
    val command = EnterQueueCommand(type = QueueTargetType.LIMITED_DROP, targetId = 1L, userId = USER_ID)

    Given("대기열 진입 결과가 WAITING인 상황에서") {
        val virtualQueueDomainService = mockk<VirtualQueueDomainService>()
        val useCase = EnterQueueUseCase(virtualQueueDomainService)
        val position = QueuePosition.of(rank = 41, seq = 42, admittedCount = 0, batchSize = 100, tickSeconds = 2)
        every { virtualQueueDomainService.enter(target, USER_ID) } returns QueueStatus.waiting(position)

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("DomainService.enter에 위임하고 aheadCount+1을 position으로 매핑한다") {
                result.status shouldBe "WAITING"
                result.position shouldBe 42L
                result.aheadCount shouldBe 41L
                result.entryToken shouldBe null
                verify(exactly = 1) { virtualQueueDomainService.enter(target, USER_ID) }
            }
        }
    }

    Given("대기열 진입 결과가 DIRECT_ADMITTED(플래그 OFF)인 상황에서") {
        val virtualQueueDomainService = mockk<VirtualQueueDomainService>()
        val useCase = EnterQueueUseCase(virtualQueueDomainService)
        val entryToken = EntryToken(raw = "token-raw", expiresAt = ZonedDateTime.now().plusMinutes(5))
        every { virtualQueueDomainService.enter(target, USER_ID) } returns QueueStatus.directEntry(entryToken)

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("position/aheadCount는 null이고 entryToken을 그대로 담는다") {
                result.status shouldBe "DIRECT_ADMITTED"
                result.position shouldBe null
                result.aheadCount shouldBe null
                result.etaSeconds shouldBe null
                result.entryToken shouldBe "token-raw"
                result.tokenExpiresAt shouldBe entryToken.expiresAt
            }
        }
    }

    Given("대기열이 포화(QueueFullException)된 상황에서") {
        val virtualQueueDomainService = mockk<VirtualQueueDomainService>()
        val useCase = EnterQueueUseCase(virtualQueueDomainService)
        every { virtualQueueDomainService.enter(target, USER_ID) } throws QueueFullException(target)

        When("execute를 호출하면") {
            Then("예외를 그대로 전파한다 (FR-7)") {
                shouldThrow<QueueFullException> { useCase.execute(command) }
            }
        }
    }
})
