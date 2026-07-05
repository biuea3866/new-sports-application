package com.sportsapp.application.featureflag.usecase

import com.sportsapp.domain.featureflag.dto.GetAuditLogsCommand
import com.sportsapp.domain.featureflag.entity.FeatureFlagAuditLog
import com.sportsapp.domain.featureflag.entity.FeatureFlagChangeType
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class GetFeatureFlagAuditLogsUseCaseTest : BehaviorSpec({

    fun auditLog() = FeatureFlagAuditLog.create(
        changeType = FeatureFlagChangeType.CREATED,
        actorUserId = 1L,
        before = null,
        after = FeatureFlagSnapshot(
            key = "demo.feature.audit",
            type = FeatureFlagType.RELEASE,
            status = FeatureFlagStatus.ACTIVE,
            strategy = EvaluationStrategy.GlobalToggle(enabled = true),
            description = null,
        ),
    )

    Given("page=0, size=10인 command로 execute를 호출하면") {
        val featureFlagDomainService = mockk<FeatureFlagDomainService>()
        val useCase = GetFeatureFlagAuditLogsUseCase(featureFlagDomainService)
        val pageable = PageRequest.of(0, 10)
        val command = GetAuditLogsCommand(key = "demo.feature.audit", pageable = pageable)
        val page = PageImpl(listOf(auditLog()), pageable, 25)
        every { featureFlagDomainService.getAuditLogs(command) } returns page

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("page/size로 만들어진 Pageable이 담긴 command가 DomainService.getAuditLogs에 그대로 전달된다") {
                verify(exactly = 1) { featureFlagDomainService.getAuditLogs(command) }
                result.pageNumber shouldBe 0
                result.pageSize shouldBe 10
            }

            Then("totalElements·totalPages를 담은 ListFeatureFlagAuditLogsResponse를 반환한다") {
                result.totalElements shouldBe 25L
                result.totalPages shouldBe 3
                result.content.size shouldBe 1
            }
        }
    }
})
