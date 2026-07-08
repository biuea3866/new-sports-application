package com.sportsapp.domain.alerting.entity

import com.sportsapp.domain.alerting.event.AlertDeliveryReadyEvent
import com.sportsapp.domain.alerting.event.AlertProcessingRequestedEvent
import com.sportsapp.domain.alerting.exception.InvalidAlertStateException
import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSignal
import com.sportsapp.domain.alerting.vo.AlertSource
import com.sportsapp.domain.alerting.vo.TelemetrySnapshot
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf

private val SIGNAL = AlertSignal(endpoint = "/pay", source = AlertSource.LATENCY, severity = AlertSeverity.WARN)

class AlertTest : BehaviorSpec({

    Given("신규 신호로 Alert를 생성하는 상황") {
        When("create를 호출하면") {
            val alert = Alert.create(SIGNAL, env = "prod")

            Then("RAISED 상태로 생성되고 아직 원지표가 없다") {
                alert.currentStatus shouldBe AlertStatus.RAISED
                alert.currentTelemetry shouldBe null
                alert.endpoint shouldBe "/pay"
                alert.source shouldBe AlertSource.LATENCY
                alert.severity shouldBe AlertSeverity.WARN
                alert.env shouldBe "prod"
            }
        }
    }

    Given("RAISED 상태로 저장된 Alert") {
        val alert = Alert.create(SIGNAL, env = "prod")

        When("requestProcessing을 호출하면") {
            alert.requestProcessing()

            Then("AlertProcessingRequestedEvent가 적재된다") {
                val events = alert.pullDomainEvents()
                events shouldHaveSize 1
                val event = events[0] as AlertProcessingRequestedEvent
                event.aggregateId shouldBe alert.id
            }
        }
    }

    Given("RAISED 상태의 Alert에 원지표가 전부 채워진 스냅샷이 도착한 상황") {
        val alert = Alert.create(SIGNAL, env = "prod")
        alert.pullDomainEvents()
        val snapshot = TelemetrySnapshot(
            metricsSummary = "p95=1200ms",
            logSamples = listOf("timeout at conn#1", "timeout at conn#2"),
            traceSamples = listOf("traceId=abc"),
        )

        When("attachTelemetry를 호출하면") {
            alert.attachTelemetry(snapshot)

            Then("ENRICHED로 전이되고 원지표가 그대로 부착된다") {
                alert.currentStatus shouldBe AlertStatus.ENRICHED
                alert.currentTelemetry shouldBe snapshot
            }

            Then("발송 이벤트 본문에 메트릭·로그·trace 섹션이 모두 렌더링된다") {
                val events = alert.pullDomainEvents()
                events shouldHaveSize 1
                val event = events[0].shouldBeInstanceOf<AlertDeliveryReadyEvent>()
                event.aggregateId shouldBe alert.id
                event.source shouldBe AlertSource.LATENCY
                event.severity shouldBe AlertSeverity.WARN
                event.env shouldBe "prod"
                event.body shouldContain "p95=1200ms"
                event.body shouldContain "timeout at conn#1"
                event.body shouldContain "traceId=abc"
            }
        }
    }

    Given("RAISED 상태의 Alert에 로그만 존재하는 부분 스냅샷이 도착한 상황") {
        val alert = Alert.create(SIGNAL, env = "prod")
        alert.pullDomainEvents()
        val snapshot = TelemetrySnapshot(
            metricsSummary = "",
            logSamples = listOf("connection refused"),
            traceSamples = emptyList(),
        )

        When("attachTelemetry를 호출하면") {
            alert.attachTelemetry(snapshot)

            Then("ENRICHED로 전이되고 로그 섹션만 본문에 렌더링된다") {
                alert.currentStatus shouldBe AlertStatus.ENRICHED
                val events = alert.pullDomainEvents()
                val event = events[0].shouldBeInstanceOf<AlertDeliveryReadyEvent>()
                event.body shouldContain "connection refused"
                event.body shouldNotContain "메트릭"
                event.body shouldNotContain "trace"
            }
        }
    }

    Given("RAISED 상태의 Alert에 로그가 4건 도착한 상황(상한 3건 초과)") {
        val alert = Alert.create(SIGNAL, env = "prod")
        alert.pullDomainEvents()
        val snapshot = TelemetrySnapshot(
            metricsSummary = "",
            logSamples = listOf("log-1", "log-2", "log-3", "log-4"),
            traceSamples = emptyList(),
        )

        When("attachTelemetry를 호출하면") {
            alert.attachTelemetry(snapshot)

            Then("최대 3건만 노출되고 초과분은 외 1건으로 표기된다") {
                val events = alert.pullDomainEvents()
                val event = events[0].shouldBeInstanceOf<AlertDeliveryReadyEvent>()
                event.body shouldContain "log-1"
                event.body shouldContain "log-2"
                event.body shouldContain "log-3"
                event.body shouldNotContain "log-4"
                event.body shouldContain "외 1건"
            }
        }
    }

    Given("RAISED 상태의 Alert에 원지표를 전혀 조회하지 못한 상황") {
        val alert = Alert.create(SIGNAL, env = "prod")
        alert.pullDomainEvents()
        val emptySnapshot = TelemetrySnapshot.empty()

        When("attachTelemetry를 호출하면") {
            alert.attachTelemetry(emptySnapshot)

            Then("ENRICHED로 전이되지만 본문은 조회 실패 문구로 채워진다") {
                alert.currentStatus shouldBe AlertStatus.ENRICHED
                val events = alert.pullDomainEvents()
                val event = events[0].shouldBeInstanceOf<AlertDeliveryReadyEvent>()
                event.body shouldBe "원인: 원지표를 조회하지 못했습니다(데이터 없음 또는 조회 실패)."
            }
        }
    }

    Given("RAISED 상태의 Alert에 공백만 담긴 비정규 빈 스냅샷이 도착한 상황") {
        val alert = Alert.create(SIGNAL, env = "prod")
        alert.pullDomainEvents()
        val blankSnapshot = TelemetrySnapshot(
            metricsSummary = "   ",
            logSamples = emptyList(),
            traceSamples = emptyList(),
        )

        When("attachTelemetry를 호출하면") {
            alert.attachTelemetry(blankSnapshot)

            Then("정규 empty가 아니어도 조회 실패 문구로 채워진다") {
                val events = alert.pullDomainEvents()
                val event = events[0].shouldBeInstanceOf<AlertDeliveryReadyEvent>()
                event.body shouldBe "원인: 원지표를 조회하지 못했습니다(데이터 없음 또는 조회 실패)."
            }
        }
    }

    Given("RAISED 상태의 Alert에 매우 긴 단일 로그 샘플이 도착한 상황") {
        val alert = Alert.create(SIGNAL, env = "prod")
        alert.pullDomainEvents()
        val longLog = "x".repeat(2000)
        val snapshot = TelemetrySnapshot(
            metricsSummary = "",
            logSamples = listOf(longLog),
            traceSamples = emptyList(),
        )

        When("attachTelemetry를 호출하면") {
            alert.attachTelemetry(snapshot)

            Then("본문의 샘플이 절단되어 원본보다 짧고 말줄임표가 붙는다") {
                val events = alert.pullDomainEvents()
                val event = events[0].shouldBeInstanceOf<AlertDeliveryReadyEvent>()
                event.body shouldContain "…"
                event.body.length shouldBeLessThan longLog.length
            }
        }
    }

    Given("ENRICHED 상태의 Alert") {
        val alert = Alert.create(SIGNAL, env = "prod")
        alert.attachTelemetry(TelemetrySnapshot.empty())
        alert.pullDomainEvents()

        When("markDelivered를 호출하면") {
            alert.markDelivered()

            Then("DELIVERED로 전이되고 발송 시각이 기록된다") {
                alert.currentStatus shouldBe AlertStatus.DELIVERED
                alert.deliveredAtValue.shouldNotBeNull()
            }
        }
    }

    Given("ENRICHED 상태에서 발송이 실패한 Alert") {
        val alert = Alert.create(SIGNAL, env = "prod")
        alert.attachTelemetry(TelemetrySnapshot.empty())
        alert.pullDomainEvents()

        When("markDeliveryFailed를 호출하면") {
            alert.markDeliveryFailed()

            Then("DELIVERY_FAILED로 전이된다") {
                alert.currentStatus shouldBe AlertStatus.DELIVERY_FAILED
            }
        }
    }

    Given("DELIVERED 상태(종료 상태)의 Alert") {
        val alert = Alert.create(SIGNAL, env = "prod")
        alert.attachTelemetry(TelemetrySnapshot.empty())
        alert.markDelivered()

        When("재발송을 위해 markDelivered를 다시 호출하면") {
            Then("InvalidAlertStateException을 던진다") {
                shouldThrow<InvalidAlertStateException> { alert.markDelivered() }
            }
        }

        When("markDeliveryFailed를 호출하면") {
            Then("InvalidAlertStateException을 던진다") {
                shouldThrow<InvalidAlertStateException> { alert.markDeliveryFailed() }
            }
        }
    }
})
