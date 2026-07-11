package com.sportsapp.application.virtualqueue.usecase

import com.sportsapp.application.virtualqueue.dto.RunAdmissionBatchCommand
import com.sportsapp.domain.virtualqueue.dto.AdmissionBatchResult
import com.sportsapp.domain.virtualqueue.service.AdmissionDomainService
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * `RunAdmissionBatchUseCase` — `AdmissionDomainService.runBatch` 위임만 검증한다(BE-07).
 */
class RunAdmissionBatchUseCaseTest : BehaviorSpec({

    val admissionDomainService = mockk<AdmissionDomainService>()
    val useCase = RunAdmissionBatchUseCase(admissionDomainService)
    val target = QueueTarget(QueueTargetType.LIMITED_DROP, 501L)

    Given("살아있는 대상에 대한 배치 admission 커맨드가 주어지면") {
        val command = RunAdmissionBatchCommand(target, batchSize = 100, staleSeconds = 60, maxEvictPerTick = 500)
        every {
            admissionDomainService.runBatch(target, batchSize = 100, staleSeconds = 60, maxEvictPerTick = 500)
        } returns AdmissionBatchResult(admittedCount = 200L, evictedCount = 2)

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("AdmissionDomainService.runBatch에 커맨드 필드를 그대로 전달하고 결과를 반환한다") {
                result.admittedCount shouldBe 200L
                result.evictedCount shouldBe 2
                verify(exactly = 1) {
                    admissionDomainService.runBatch(target, batchSize = 100, staleSeconds = 60, maxEvictPerTick = 500)
                }
            }
        }
    }
})
