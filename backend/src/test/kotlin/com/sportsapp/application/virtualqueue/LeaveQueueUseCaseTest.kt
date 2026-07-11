package com.sportsapp.application.virtualqueue

import com.sportsapp.application.virtualqueue.dto.LeaveQueueCommand
import com.sportsapp.application.virtualqueue.usecase.LeaveQueueUseCase
import com.sportsapp.domain.virtualqueue.service.VirtualQueueDomainService
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

private const val USER_ID = 300L

/**
 * `LeaveQueueUseCase` — `VirtualQueueDomainService.leave` 위임(execute ≤10줄)을 검증한다 (BE-06).
 */
class LeaveQueueUseCaseTest : BehaviorSpec({

    val target = QueueTarget(QueueTargetType.LIMITED_DROP, 7L)
    val command = LeaveQueueCommand(type = QueueTargetType.LIMITED_DROP, targetId = 7L, userId = USER_ID)

    Given("명시적 이탈을 요청하는 상황에서") {
        val virtualQueueDomainService = mockk<VirtualQueueDomainService>()
        val useCase = LeaveQueueUseCase(virtualQueueDomainService)
        every { virtualQueueDomainService.leave(target, USER_ID) } returns Unit

        When("execute를 호출하면") {
            useCase.execute(command)

            Then("DomainService.leave에 위임한다") {
                verify(exactly = 1) { virtualQueueDomainService.leave(target, USER_ID) }
            }
        }
    }

    Given("이미 이탈한 사용자가 재차 이탈을 요청하는(멱등) 상황에서") {
        val virtualQueueDomainService = mockk<VirtualQueueDomainService>()
        val useCase = LeaveQueueUseCase(virtualQueueDomainService)
        every { virtualQueueDomainService.leave(target, USER_ID) } returns Unit

        When("execute를 두 번 호출하면") {
            useCase.execute(command)
            useCase.execute(command)

            Then("예외 없이 두 번 모두 위임한다") {
                verify(exactly = 2) { virtualQueueDomainService.leave(target, USER_ID) }
            }
        }
    }
})
