package com.sportsapp.domain.alerting.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class AlertSeverityTest : BehaviorSpec({

    Given("CRITICAL 심각도") {
        When("discordColor를 호출하면") {
            val color = AlertSeverity.CRITICAL.discordColor()

            Then("WARN·INFO와 구분되는 색상값을 반환한다") {
                color shouldNotBe AlertSeverity.WARN.discordColor()
                color shouldNotBe AlertSeverity.INFO.discordColor()
            }
        }
    }

    Given("WARN 심각도") {
        When("discordColor를 호출하면") {
            val color = AlertSeverity.WARN.discordColor()

            Then("INFO와 구분되는 색상값을 반환한다") {
                color shouldNotBe AlertSeverity.INFO.discordColor()
            }
        }
    }

    Given("동일한 INFO 심각도 두 번 조회") {
        When("discordColor를 반복 호출하면") {
            Then("항상 동일한 색상값을 반환한다") {
                AlertSeverity.INFO.discordColor() shouldBe AlertSeverity.INFO.discordColor()
            }
        }
    }
})
