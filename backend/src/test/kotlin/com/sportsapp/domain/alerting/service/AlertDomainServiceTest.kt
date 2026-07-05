package com.sportsapp.domain.alerting.service

import com.sportsapp.domain.alerting.dto.RaiseAlertCommand
import com.sportsapp.domain.alerting.entity.Alert
import com.sportsapp.domain.alerting.entity.AlertStatus
import com.sportsapp.domain.alerting.event.AlertDeliveryReadyEvent
import com.sportsapp.domain.alerting.event.AlertProcessingRequestedEvent
import com.sportsapp.domain.alerting.exception.IncidentAnalysisException
import com.sportsapp.domain.alerting.gateway.IncidentAnalysisGateway
import com.sportsapp.domain.alerting.gateway.TelemetryQueryGateway
import com.sportsapp.domain.alerting.repository.AlertCooldownRepository
import com.sportsapp.domain.alerting.repository.AlertRepository
import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSignal
import com.sportsapp.domain.alerting.vo.AlertSource
import com.sportsapp.domain.alerting.vo.IncidentAnalysis
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
        incidentAnalysisGateway: IncidentAnalysisGateway = mockk(),
        domainEventPublisher: DomainEventPublisher = mockk(),
        env: String = ENV,
    ) = AlertDomainService(
        alertRepository = alertRepository,
        alertCooldownRepository = alertCooldownRepository,
        telemetryQueryGateway = telemetryQueryGateway,
        incidentAnalysisGateway = incidentAnalysisGateway,
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

    Given("RAISED 상태 Alert에 대한 process 요청, LLM 분석이 성공하는 상황") {
        val alertRepository = mockk<AlertRepository>()
        val telemetryQueryGateway = mockk<TelemetryQueryGateway>()
        val incidentAnalysisGateway = mockk<IncidentAnalysisGateway>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = buildService(
            alertRepository = alertRepository,
            telemetryQueryGateway = telemetryQueryGateway,
            incidentAnalysisGateway = incidentAnalysisGateway,
            domainEventPublisher = domainEventPublisher,
        )
        val alert = raisedAlert()
        val snapshot = TelemetrySnapshot(metricsSummary = "p95=1200ms", logSamples = listOf("timeout"), traceSamples = emptyList())
        val analysis = IncidentAnalysis(errorType = "TimeoutException", causeEstimation = "DB 커넥션 풀 고갈", remediation = "풀 확대", included = true)
        val publishedEventsSlot = slot<List<DomainEvent>>()

        every { alertRepository.findById(ALERT_ID) } returns alert
        every { telemetryQueryGateway.queryContext(any(), Duration.ofMinutes(10)) } returns snapshot
        every { incidentAnalysisGateway.analyze(any()) } returns analysis
        every { alertRepository.save(alert) } returns alert
        every { domainEventPublisher.publishAll(capture(publishedEventsSlot)) } returns Unit

        When("process를 호출하면") {
            service.process(ALERT_ID)

            Then("ANALYZED로 전이되고 included=true인 채로 발송 이벤트가 발행된다") {
                alert.currentStatus shouldBe AlertStatus.ANALYZED
                alert.currentAnalysis?.included shouldBe true
                publishedEventsSlot.captured shouldHaveSize 1
                publishedEventsSlot.captured[0].shouldBeInstanceOf<AlertDeliveryReadyEvent>()
            }
        }
    }

    Given("RAISED 상태 Alert에 대한 process 요청, IncidentAnalysisGateway가 예외를 던지는 상황") {
        val alertRepository = mockk<AlertRepository>()
        val telemetryQueryGateway = mockk<TelemetryQueryGateway>()
        val incidentAnalysisGateway = mockk<IncidentAnalysisGateway>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = buildService(
            alertRepository = alertRepository,
            telemetryQueryGateway = telemetryQueryGateway,
            incidentAnalysisGateway = incidentAnalysisGateway,
            domainEventPublisher = domainEventPublisher,
        )
        val alert = raisedAlert()
        val snapshot = TelemetrySnapshot(metricsSummary = "p95=1200ms", logSamples = emptyList(), traceSamples = emptyList())
        val publishedEventsSlot = slot<List<DomainEvent>>()

        every { alertRepository.findById(ALERT_ID) } returns alert
        every { telemetryQueryGateway.queryContext(any(), Duration.ofMinutes(10)) } returns snapshot
        every { incidentAnalysisGateway.analyze(any()) } throws IncidentAnalysisException("LLM timeout")
        every { alertRepository.save(alert) } returns alert
        every { domainEventPublisher.publishAll(capture(publishedEventsSlot)) } returns Unit

        When("process를 호출하면") {
            service.process(ALERT_ID)

            Then("FALLBACK 분석으로 대체되고 발송 이벤트는 여전히 발행된다") {
                alert.currentStatus shouldBe AlertStatus.FALLBACK
                alert.currentAnalysis?.included shouldBe false
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

            Then("쿨다운·분석 없이 SELF_CHECK/INFO 발송 이벤트를 발행한다") {
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
