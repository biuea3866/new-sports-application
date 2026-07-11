package com.sportsapp.application.virtualqueue

import com.sportsapp.application.virtualqueue.dto.GetQueueStatsCommand
import com.sportsapp.application.virtualqueue.usecase.GetQueueStatsUseCase
import com.sportsapp.domain.virtualqueue.service.VirtualQueueDomainService
import com.sportsapp.domain.virtualqueue.vo.QueueStats
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * `GetQueueStatsUseCase` — `VirtualQueueDomainService.stats` 위임(execute ≤10줄, FR-11)과
 * QueueStats → QueueStatsResponse 변환을 검증한다 (BE-06).
 *
 * stats 산출 방침: waitingCount·admittedCount는 Store 즉시 조회값(DomainService.stats가 이미
 * 조합), admissionRatePerSec·avgWaitSeconds·p95WaitSeconds는 BE-10 Observability 미연동 상태라
 * DomainService가 반환한 0.0 placeholder를 그대로 통과시킨다 — 이 UseCase가 Micrometer를
 * 직접 조회하지 않는다.
 */
class GetQueueStatsUseCaseTest : BehaviorSpec({

    val target = QueueTarget(QueueTargetType.LIMITED_DROP, 9L)
    val command = GetQueueStatsCommand(type = QueueTargetType.LIMITED_DROP, targetId = 9L)

    Given("대기 250명·누적 입장 100명인 대상의 통계를 조회하는 상황에서") {
        val virtualQueueDomainService = mockk<VirtualQueueDomainService>()
        val useCase = GetQueueStatsUseCase(virtualQueueDomainService)
        every { virtualQueueDomainService.stats(target) } returns QueueStats.of(waitingCount = 250L, admittedCount = 100L)

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("DomainService.stats에 위임하고 Store 조회값을 그대로 매핑한다") {
                result.waitingCount shouldBe 250L
                result.admittedCount shouldBe 100L
                verify(exactly = 1) { virtualQueueDomainService.stats(target) }
            }

            Then("지표성 필드는 DomainService가 반환한 0.0 placeholder를 그대로 통과시킨다") {
                result.admissionRatePerSec shouldBe 0.0
                result.avgWaitSeconds shouldBe 0.0
                result.p95WaitSeconds shouldBe 0.0
            }
        }
    }

    Given("대기 인원이 0건인 빈 큐의 통계를 조회하는 상황에서") {
        val virtualQueueDomainService = mockk<VirtualQueueDomainService>()
        val useCase = GetQueueStatsUseCase(virtualQueueDomainService)
        every { virtualQueueDomainService.stats(target) } returns QueueStats.of(waitingCount = 0L, admittedCount = 0L)

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("예외 없이 0건 통계를 반환한다 (빈 상태 엣지)") {
                result.waitingCount shouldBe 0L
                result.admittedCount shouldBe 0L
            }
        }
    }
})
