package com.sportsapp.domain.alerting.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class AlertSignalTest : BehaviorSpec({

    Given("endpoint·source·severity로 구성된 신호") {
        val signal = AlertSignal(
            endpoint = "/pay",
            source = AlertSource.LATENCY,
            severity = AlertSeverity.WARN,
        )

        When("cooldownKey(env)를 호출하면") {
            val key = signal.cooldownKey("prod")

            Then("INFRA-01 계약 형식(alerting:cooldown:{env}:{endpoint}:{source}:{severity})으로 소문자 생성한다") {
                key shouldBe "alerting:cooldown:prod:/pay:latency:warn"
            }
        }
    }

    Given("CRITICAL 심각도의 배포 실패 신호") {
        val signal = AlertSignal(
            endpoint = "/checkout",
            source = AlertSource.DEPLOYMENT,
            severity = AlertSeverity.CRITICAL,
        )

        When("cooldownKey(env)를 dev 환경으로 호출하면") {
            val key = signal.cooldownKey("dev")

            Then("env·source·severity가 모두 소문자로 반영된다") {
                key shouldBe "alerting:cooldown:dev:/checkout:deployment:critical"
            }
        }
    }
})
