package com.sportsapp.domain.alerting.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class IncidentAnalysisTest : BehaviorSpec({

    Given("LLM 분석 실패 상황") {
        When("IncidentAnalysis.fallback()을 호출하면") {
            val analysis = IncidentAnalysis.fallback()

            Then("included가 false인 폴백 분석을 생성한다") {
                analysis.included shouldBe false
            }
        }
    }

    Given("LLM이 정상 분석한 결과") {
        When("직접 생성자로 구성하면") {
            val analysis = IncidentAnalysis(
                errorType = "TimeoutException",
                causeEstimation = "DB 커넥션 풀 고갈",
                remediation = "커넥션 풀 사이즈 확대",
                included = true,
            )

            Then("included가 true로 유지된다") {
                analysis.included shouldBe true
            }
        }
    }
})
