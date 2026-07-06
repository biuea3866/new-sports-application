package com.sportsapp.domain.alerting.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class TelemetrySnapshotTest : BehaviorSpec({

    Given("정규 empty 스냅샷") {
        val snapshot = TelemetrySnapshot.empty()

        Then("isEmpty는 true다") {
            snapshot.isEmpty shouldBe true
        }
    }

    Given("메트릭 요약이 공백만이고 샘플이 0건인 비정규 빈 스냅샷") {
        val snapshot = TelemetrySnapshot(
            metricsSummary = "   ",
            logSamples = emptyList(),
            traceSamples = emptyList(),
        )

        Then("isEmpty는 true다") {
            snapshot.isEmpty shouldBe true
        }
    }

    Given("메트릭 요약이 채워진 스냅샷") {
        val snapshot = TelemetrySnapshot(
            metricsSummary = "p95=1200ms",
            logSamples = emptyList(),
            traceSamples = emptyList(),
        )

        Then("isEmpty는 false다") {
            snapshot.isEmpty shouldBe false
        }
    }

    Given("로그 샘플만 존재하는 스냅샷") {
        val snapshot = TelemetrySnapshot(
            metricsSummary = "",
            logSamples = listOf("connection refused"),
            traceSamples = emptyList(),
        )

        Then("isEmpty는 false다") {
            snapshot.isEmpty shouldBe false
        }
    }
})
