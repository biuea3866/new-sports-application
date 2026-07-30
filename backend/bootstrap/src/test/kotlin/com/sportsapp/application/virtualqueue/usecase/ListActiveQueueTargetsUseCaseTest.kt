package com.sportsapp.application.virtualqueue.usecase

import com.sportsapp.domain.virtualqueue.service.AdmissionDomainService
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * `ListActiveQueueTargetsUseCase` — `AdmissionDomainService.activeTargets` 위임만 검증한다.
 */
class ListActiveQueueTargetsUseCaseTest : BehaviorSpec({

    val admissionDomainService = mockk<AdmissionDomainService>()
    val useCase = ListActiveQueueTargetsUseCase(admissionDomainService)

    Given("활성 대상이 2건 존재하는 상태에서") {
        val targets = setOf(
            QueueTarget(QueueTargetType.LIMITED_DROP, 901L),
            QueueTarget(QueueTargetType.TICKETING_EVENT, 902L),
        )
        every { admissionDomainService.activeTargets() } returns targets

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("AdmissionDomainService.activeTargets 결과를 그대로 반환한다") {
                result shouldBe targets
                verify(exactly = 1) { admissionDomainService.activeTargets() }
            }
        }
    }

    Given("활성 대상이 없는 상태에서") {
        every { admissionDomainService.activeTargets() } returns emptySet()

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("빈 집합을 반환한다") {
                result shouldBe emptySet()
            }
        }
    }
})
