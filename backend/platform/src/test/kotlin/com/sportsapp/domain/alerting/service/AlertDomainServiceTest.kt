package com.sportsapp.domain.alerting.service

import com.sportsapp.domain.alerting.dto.RaiseAlertCommand
import com.sportsapp.domain.alerting.entity.Alert
import com.sportsapp.domain.alerting.entity.AlertStatus
import com.sportsapp.domain.alerting.event.AlertDeliveryReadyEvent
import com.sportsapp.domain.alerting.event.AlertProcessingRequestedEvent
import com.sportsapp.domain.alerting.gateway.TelemetryQueryGateway
import com.sportsapp.domain.alerting.repository.AlertCooldownRepository
import com.sportsapp.domain.alerting.repository.AlertRepository
import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSignal
import com.sportsapp.domain.alerting.vo.AlertSource
import com.sportsapp.domain.alerting.vo.TelemetrySnapshot
import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Duration

private const val ALERT_ID = 0L
private const val ENDPOINT = "/pay"
private const val ENV = "prod"

private fun signal(): AlertSignal = AlertSignal(ENDPOINT, AlertSource.LATENCY, AlertSeverity.WARN)

private fun raisedAlert(): Alert = Alert.create(signal(), ENV)

class AlertDomainServiceTest : BehaviorSpec({

    fun buildService(
        alertRepository: AlertRepository = mockk(),
        alertCooldownRepository: AlertCooldownRepository = mockk(),
        telemetryQueryGateway: TelemetryQueryGateway = mockk(),
        domainEventPublisher: DomainEventPublisher = mockk(),
        env: String = ENV,
    ) = AlertDomainService(
        alertRepository = alertRepository,
        alertCooldownRepository = alertCooldownRepository,
        telemetryQueryGateway = telemetryQueryGateway,
        domainEventPublisher = domainEventPublisher,
        env = env,
    )

    Given("쿨다운을 획득한 raise 요청") {
        val alertRepository = mockk<AlertRepository>()
        val alertCooldownRepository = mockk<AlertCooldownRepository>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = buildService(
            alertRepository = alertRepository,
            alertCooldownRepository = alertCooldownRepository,
            domainEventPublisher = domainEventPublisher,
        )
        val command = RaiseAlertCommand(endpoint = ENDPOINT, source = AlertSource.LATENCY, severity = AlertSeverity.WARN, env = ENV)
        val savedAlertSlot = slot<Alert>()
        val publishedEventsSlot = slot<List<DomainEvent>>()

        every { alertCooldownRepository.tryAcquire(signal(), ENV, Duration.ofMinutes(15)) } returns true
        every { alertRepository.save(capture(savedAlertSlot)) } answers { savedAlertSlot.captured }
        every { domainEventPublisher.publishAll(capture(publishedEventsSlot)) } returns Unit

        When("raise를 호출하면") {
            val result = service.raise(command)

            Then("RAISED 상태의 Alert가 저장되고 AlertProcessingRequestedEvent가 발행된다") {
                val alert = result.shouldNotBeNull()
                alert.currentStatus shouldBe AlertStatus.RAISED
                verify(exactly = 1) { alertRepository.save(any()) }
                publishedEventsSlot.captured shouldHaveSize 1
                val event = publishedEventsSlot.captured[0].shouldBeInstanceOf<AlertProcessingRequestedEvent>()
                event.aggregateId shouldBe alert.id
            }
        }
    }

    Given("쿨다운을 획득하지 못한 raise 요청") {
        val alertRepository = mockk<AlertRepository>()
        val alertCooldownRepository = mockk<AlertCooldownRepository>()
        val service = buildService(alertRepository = alertRepository, alertCooldownRepository = alertCooldownRepository)
        val command = RaiseAlertCommand(endpoint = ENDPOINT, source = AlertSource.LATENCY, severity = AlertSeverity.WARN, env = ENV)

        every { alertCooldownRepository.tryAcquire(signal(), ENV, Duration.ofMinutes(15)) } returns false

        When("raise를 호출하면") {
            val result = service.raise(command)

            Then("Alert를 생성하지 않고 null을 반환한다(억제)") {
                result.shouldBeNull()
                verify(exactly = 0) { alertRepository.save(any()) }
            }
        }
    }

    Given("서비스 자신의 app.env(배포 환경)와 command.env(webhook payload 출처)가 서로 다른 raise 요청") {
        val alertRepository = mockk<AlertRepository>()
        val alertCooldownRepository = mockk<AlertCooldownRepository>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val instanceEnv = "instance-env"
        val payloadEnv = "payload-env"
        val service = buildService(
            alertRepository = alertRepository,
            alertCooldownRepository = alertCooldownRepository,
            domainEventPublisher = domainEventPublisher,
            env = instanceEnv,
        )
        val command = RaiseAlertCommand(endpoint = ENDPOINT, source = AlertSource.LATENCY, severity = AlertSeverity.WARN, env = payloadEnv)
        val savedAlertSlot = slot<Alert>()
        val publishedEventsSlot = slot<List<DomainEvent>>()

        every { alertCooldownRepository.tryAcquire(signal(), payloadEnv, Duration.ofMinutes(15)) } returns true
        every { alertRepository.save(capture(savedAlertSlot)) } answers { savedAlertSlot.captured }
        every { domainEventPublisher.publishAll(capture(publishedEventsSlot)) } returns Unit

        When("raise를 호출하면") {
            val result = service.raise(command)

            Then("쿨다운 키는 command.env(단일 출처)로 생성되고, 서비스 자신의 app.env는 사용되지 않는다") {
                result.shouldNotBeNull()
                verify(exactly = 1) { alertCooldownRepository.tryAcquire(signal(), payloadEnv, Duration.ofMinutes(15)) }
                verify(exactly = 0) { alertCooldownRepository.tryAcquire(signal(), instanceEnv, any()) }
            }
        }
    }

    Given("RAISED 상태 Alert에 대한 process 요청, 원지표가 정상 조회되는 상황") {
        val alertRepository = mockk<AlertRepository>()
        val telemetryQueryGateway = mockk<TelemetryQueryGateway>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = buildService(
            alertRepository = alertRepository,
            telemetryQueryGateway = telemetryQueryGateway,
            domainEventPublisher = domainEventPublisher,
        )
        val alert = raisedAlert()
        val snapshot = TelemetrySnapshot(metricsSummary = "p95=1200ms", logSamples = listOf("timeout"), traceSamples = emptyList())
        val publishedEventsSlot = slot<List<DomainEvent>>()

        every { alertRepository.findById(ALERT_ID) } returns alert
        every { telemetryQueryGateway.queryContext(any(), Duration.ofMinutes(10)) } returns snapshot
        every { alertRepository.save(alert) } returns alert
        every { domainEventPublisher.publishAll(capture(publishedEventsSlot)) } returns Unit

        When("process를 호출하면") {
            service.process(ALERT_ID)

            Then("ENRICHED로 전이되고 조회한 원지표가 그대로 부착된 채 발송 이벤트가 발행된다") {
                alert.currentStatus shouldBe AlertStatus.ENRICHED
                alert.currentTelemetry shouldBe snapshot
                publishedEventsSlot.captured shouldHaveSize 1
                publishedEventsSlot.captured[0].shouldBeInstanceOf<AlertDeliveryReadyEvent>()
            }
        }
    }

    Given("RAISED 상태 Alert에 대한 process 요청, 원지표를 전혀 조회하지 못한 상황") {
        val alertRepository = mockk<AlertRepository>()
        val telemetryQueryGateway = mockk<TelemetryQueryGateway>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = buildService(
            alertRepository = alertRepository,
            telemetryQueryGateway = telemetryQueryGateway,
            domainEventPublisher = domainEventPublisher,
        )
        val alert = raisedAlert()
        val emptySnapshot = TelemetrySnapshot.empty()
        val publishedEventsSlot = slot<List<DomainEvent>>()

        every { alertRepository.findById(ALERT_ID) } returns alert
        every { telemetryQueryGateway.queryContext(any(), Duration.ofMinutes(10)) } returns emptySnapshot
        every { alertRepository.save(alert) } returns alert
        every { domainEventPublisher.publishAll(capture(publishedEventsSlot)) } returns Unit

        When("process를 호출하면") {
            service.process(ALERT_ID)

            Then("ENRICHED로 전이되고 발송 이벤트는 여전히 발행된다(TelemetryQueryGateway는 예외를 던지지 않으므로 폴백 분기 없음)") {
                alert.currentStatus shouldBe AlertStatus.ENRICHED
                alert.currentTelemetry shouldBe emptySnapshot
                publishedEventsSlot.captured shouldHaveSize 1
                publishedEventsSlot.captured[0].shouldBeInstanceOf<AlertDeliveryReadyEvent>()
            }
        }
    }

    Given("1시간 주기 self-check 트리거") {
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = buildService(domainEventPublisher = domainEventPublisher, env = ENV)
        val publishedEventSlot = slot<DomainEvent>()

        every { domainEventPublisher.publish(capture(publishedEventSlot)) } returns Unit

        When("selfCheck를 호출하면") {
            service.selfCheck()

            Then("쿨다운·원지표 조회 없이 SELF_CHECK/INFO 발송 이벤트를 발행한다") {
                val event = publishedEventSlot.captured.shouldBeInstanceOf<AlertDeliveryReadyEvent>()
                event.source shouldBe AlertSource.SELF_CHECK
                event.severity shouldBe AlertSeverity.INFO
                event.env shouldBe ENV
            }
        }
    }

    Given("90일 보존 정책 정리 배치 요청") {
        val alertRepository = mockk<AlertRepository>()
        val service = buildService(alertRepository = alertRepository)
        val cutoffSlot = slot<java.time.ZonedDateTime>()

        every { alertRepository.deleteRaisedBefore(capture(cutoffSlot)) } returns 3L

        When("purgeExpiredAlerts(90)을 호출하면") {
            val deletedCount = service.purgeExpiredAlerts(retentionDays = 90L)

            Then("현재 시각으로부터 90일 이전 cutoff로 삭제를 위임하고 삭제 건수를 반환한다") {
                deletedCount shouldBe 3L
                val now = java.time.ZonedDateTime.now()
                val expectedCutoff = now.minusDays(90)
                cutoffSlot.captured.isBefore(now.minusDays(89)) shouldBe true
                cutoffSlot.captured.isAfter(expectedCutoff.minusSeconds(5)) shouldBe true
            }
        }
    }
})
