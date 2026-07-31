package com.sportsapp.presentation.alerting.worker

import com.sportsapp.application.alerting.usecase.ProcessAlertUseCase
import com.sportsapp.domain.alerting.event.AlertProcessingRequestedEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

private const val ALERT_ID = 42L

class AlertProcessingEventWorkerTest : BehaviorSpec({

    Given("AlertProcessingRequestedEvent가 커밋된 상황") {
        val processAlertUseCase = mockk<ProcessAlertUseCase>()
        val worker = AlertProcessingEventWorker(processAlertUseCase)
        val event = AlertProcessingRequestedEvent(alertId = ALERT_ID)

        every { processAlertUseCase.execute(ALERT_ID) } returns Unit

        When("onProcessingRequested를 호출하면") {
            worker.onProcessingRequested(event)

            Then("동일 alertId로 ProcessAlertUseCase를 1회 위임한다") {
                verify(exactly = 1) { processAlertUseCase.execute(ALERT_ID) }
            }
        }
    }

    Given("ProcessAlertUseCase가 예외를 던지는 상황") {
        val processAlertUseCase = mockk<ProcessAlertUseCase>()
        val worker = AlertProcessingEventWorker(processAlertUseCase)
        val event = AlertProcessingRequestedEvent(alertId = ALERT_ID)

        every { processAlertUseCase.execute(ALERT_ID) } throws RuntimeException("boom")

        When("onProcessingRequested를 호출하면") {
            Then("예외를 로깅만 하고 전파하지 않는다") {
                worker.onProcessingRequested(event)
            }
        }
    }
})
