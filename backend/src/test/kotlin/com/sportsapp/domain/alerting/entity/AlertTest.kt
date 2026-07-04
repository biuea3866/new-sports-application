package com.sportsapp.domain.alerting.entity

import com.sportsapp.domain.alerting.event.AlertDeliveryReadyEvent
import com.sportsapp.domain.alerting.event.AlertProcessingRequestedEvent
import com.sportsapp.domain.alerting.exception.InvalidAlertStateException
import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSignal
import com.sportsapp.domain.alerting.vo.AlertSource
import com.sportsapp.domain.alerting.vo.IncidentAnalysis
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private val SIGNAL = AlertSignal(endpoint = "/pay", source = AlertSource.LATENCY, severity = AlertSeverity.WARN)

private fun analyzedResult(): IncidentAnalysis = IncidentAnalysis(
    errorType = "TimeoutException",
    causeEstimation = "DB 커넥션 풀 고갈",
    remediation = "커넥션 풀 사이즈 확대",
    included = true,
)

class AlertTest : BehaviorSpec({

    Given("신규 신호로 Alert를 생성하는 상황") {
        When("create를 호출하면") {
            val alert = Alert.create(SIGNAL, env = "prod")

            Then("RAISED 상태로 생성되고 아직 분석 결과가 없다") {
                alert.currentStatus shouldBe AlertStatus.RAISED
                alert.currentAnalysis shouldBe null
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

    Given("RAISED 상태의 Alert에 LLM 분석이 성공한 상황") {
        val alert = Alert.create(SIGNAL, env = "prod")
        alert.pullDomainEvents()
        val analysis = analyzedResult()

        When("attachAnalysis를 호출하면") {
            alert.attachAnalysis(analysis)

            Then("ANALYZED로 전이되고 발송 이벤트가 등록된다") {
                alert.currentStatus shouldBe AlertStatus.ANALYZED
                alert.currentAnalysis shouldBe analysis
                val events = alert.pullDomainEvents()
                events shouldHaveSize 1
                val event = events[0].shouldBeInstanceOf<AlertDeliveryReadyEvent>()
                event.aggregateId shouldBe alert.id
                event.source shouldBe AlertSource.LATENCY
                event.severity shouldBe AlertSeverity.WARN
                event.env shouldBe "prod"
            }
        }
    }

    Given("RAISED 상태의 Alert에 LLM 분석이 실패해 폴백 분석이 도착한 상황") {
        val alert = Alert.create(SIGNAL, env = "prod")
        alert.pullDomainEvents()
        val fallback = IncidentAnalysis.fallback()

        When("attachAnalysis를 호출하면") {
            alert.attachAnalysis(fallback)

            Then("FALLBACK으로 전이되지만 발송 이벤트는 여전히 등록된다") {
                alert.currentStatus shouldBe AlertStatus.FALLBACK
                alert.currentAnalysis?.included shouldBe false
                val events = alert.pullDomainEvents()
                events shouldHaveSize 1
                events[0].shouldBeInstanceOf<AlertDeliveryReadyEvent>()
            }
        }
    }

    Given("ANALYZED 상태의 Alert") {
        val alert = Alert.create(SIGNAL, env = "prod")
        alert.attachAnalysis(analyzedResult())
        alert.pullDomainEvents()

        When("markDelivered를 호출하면") {
            alert.markDelivered()

            Then("DELIVERED로 전이되고 발송 시각이 기록된다") {
                alert.currentStatus shouldBe AlertStatus.DELIVERED
                alert.deliveredAtValue.shouldNotBeNull()
            }
        }
    }

    Given("ANALYZED 상태에서 발송이 실패한 Alert") {
        val alert = Alert.create(SIGNAL, env = "prod")
        alert.attachAnalysis(analyzedResult())
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
        alert.attachAnalysis(analyzedResult())
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
