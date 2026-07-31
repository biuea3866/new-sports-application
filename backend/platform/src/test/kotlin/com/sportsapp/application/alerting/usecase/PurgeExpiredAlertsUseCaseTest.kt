package com.sportsapp.application.alerting.usecase

import com.sportsapp.domain.alerting.service.AlertDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class PurgeExpiredAlertsUseCaseTest : BehaviorSpec({

    Given("90일 보존 정책 정리 배치 cron 트리거") {
        val alertDomainService = mockk<AlertDomainService>()
        val useCase = PurgeExpiredAlertsUseCase(alertDomainService, retentionDays = 90L)

        every { alertDomainService.purgeExpiredAlerts(90L) } returns 5L

        When("execute를 호출하면") {
            val deletedCount = useCase.execute()

            Then("설정된 보존 기간으로 AlertDomainService.purgeExpiredAlerts를 위임 호출하고 삭제 건수를 반환한다") {
                deletedCount shouldBe 5L
                verify(exactly = 1) { alertDomainService.purgeExpiredAlerts(90L) }
            }
        }
    }
})
