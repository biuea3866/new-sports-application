package com.sportsapp.presentation.alerting.worker

import com.sportsapp.application.notification.dto.SendRawNotificationCommand
import com.sportsapp.application.notification.usecase.SendRawNotificationUseCase
import com.sportsapp.domain.alerting.event.AlertDeliveryReadyEvent
import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSource
import com.sportsapp.domain.notification.dto.NotificationResult
import com.sportsapp.domain.notification.vo.NotificationChannel
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

private const val ALERT_ID = 7L
private const val RECIPIENT_USER_ID = 1L
private const val TITLE = "결제 API 지연"
private const val BODY = "P95 latency 2500ms 초과"
private const val ENV = "prod"

class AlertDeliveryEventWorkerTest : BehaviorSpec({

    Given("AlertDeliveryReadyEvent가 커밋된 상황") {
        val sendRawNotificationUseCase = mockk<SendRawNotificationUseCase>()
        val worker = AlertDeliveryEventWorker(
            sendRawNotificationUseCase = sendRawNotificationUseCase,
            recipientUserId = RECIPIENT_USER_ID,
        )
        val event = AlertDeliveryReadyEvent(
            alertId = ALERT_ID,
            title = TITLE,
            body = BODY,
            source = AlertSource.LATENCY,
            severity = AlertSeverity.CRITICAL,
            env = ENV,
        )
        val commandSlot: CapturingSlot<SendRawNotificationCommand> = slot()
        every { sendRawNotificationUseCase.execute(capture(commandSlot)) } returns mockk<NotificationResult>()

        When("onDeliveryReady를 호출하면") {
            worker.onDeliveryReady(event)

            Then("DISCORD 채널·recipientUserId로 SendRawNotificationUseCase를 1회 호출한다") {
                verify(exactly = 1) { sendRawNotificationUseCase.execute(any()) }
                commandSlot.captured.channel shouldBe NotificationChannel.DISCORD
                commandSlot.captured.userId shouldBe RECIPIENT_USER_ID
            }

            Then("발송 payload에 title/body/source/severity/env가 모두 포함된다") {
                val payload = commandSlot.captured.payload
                payload["_title"] shouldBe TITLE
                payload["_body"] shouldBe BODY
                payload["source"] shouldBe AlertSource.LATENCY.name
                payload["severity"] shouldBe AlertSeverity.CRITICAL.name
                payload["env"] shouldBe ENV
            }
        }
    }

    Given("SendRawNotificationUseCase가 예외를 던지는 상황") {
        val sendRawNotificationUseCase = mockk<SendRawNotificationUseCase>()
        val worker = AlertDeliveryEventWorker(
            sendRawNotificationUseCase = sendRawNotificationUseCase,
            recipientUserId = RECIPIENT_USER_ID,
        )
        val event = AlertDeliveryReadyEvent(
            alertId = ALERT_ID,
            title = TITLE,
            body = BODY,
            source = AlertSource.SELF_CHECK,
            severity = AlertSeverity.INFO,
            env = ENV,
        )
        every { sendRawNotificationUseCase.execute(any()) } throws RuntimeException("discord down")

        When("onDeliveryReady를 호출하면") {
            Then("예외를 로깅만 하고 전파하지 않는다") {
                worker.onDeliveryReady(event)
            }
        }
    }
})
