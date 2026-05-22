package com.sportsapp.domain.mcp

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class McpAnomalyDetectorTest : BehaviorSpec({

    val detector = McpAnomalyDetector()

    Given("[U-01] 베이스라인 평균보다 2배 이상 호출 시") {
        val baseline = 10.0
        val currentCount = 25L

        When("isAnomaly를 호출하면") {
            val result = detector.isAnomaly(
                baselineAverage = baseline,
                currentHourCount = currentCount,
            )

            Then("[U-01] 비정상으로 판정된다") {
                result shouldBe true
            }
        }
    }

    Given("[U-02] 베이스라인 평균보다 2배 미만 호출 시") {
        val baseline = 10.0
        val currentCount = 19L

        When("isAnomaly를 호출하면") {
            val result = detector.isAnomaly(
                baselineAverage = baseline,
                currentHourCount = currentCount,
            )

            Then("[U-02] 정상으로 판정된다") {
                result shouldBe false
            }
        }
    }

    Given("[U-03] 베이스라인 평균이 0일 때 호출 수가 임계값(MIN_ABSOLUTE_THRESHOLD) 미만 시") {
        val baseline = 0.0
        val currentCount = McpAnomalyDetector.MIN_ABSOLUTE_THRESHOLD - 1L

        When("isAnomaly를 호출하면") {
            val result = detector.isAnomaly(
                baselineAverage = baseline,
                currentHourCount = currentCount,
            )

            Then("[U-03] 정상으로 판정된다 (절대값 임계 미달)") {
                result shouldBe false
            }
        }
    }

    Given("[U-04] 베이스라인 평균이 0이고 현재 호출 수가 절대값 임계 이상 시") {
        val baseline = 0.0
        val currentCount = McpAnomalyDetector.MIN_ABSOLUTE_THRESHOLD.toLong()

        When("isAnomaly를 호출하면") {
            val result = detector.isAnomaly(
                baselineAverage = baseline,
                currentHourCount = currentCount,
            )

            Then("[U-04] 비정상으로 판정된다 (절대값 임계 초과)") {
                result shouldBe true
            }
        }
    }

    Given("[U-05] 정확히 배율 임계값(SPIKE_RATIO=2.0)의 2배인 경우") {
        val baseline = 10.0
        val currentCount = 20L

        When("isAnomaly를 호출하면 (boundary: baseline * SPIKE_RATIO = 20.0)") {
            val result = detector.isAnomaly(
                baselineAverage = baseline,
                currentHourCount = currentCount,
            )

            Then("[U-05] 비정상으로 판정된다 (경계값 포함)") {
                result shouldBe true
            }
        }
    }

    Given("[U-06] cold-start 판정 — 토큰 생성 후 14일 이내") {
        val createdAt = ZonedDateTime.now().minusDays(13)

        When("isColdStart를 호출하면") {
            val result = detector.isColdStart(tokenCreatedAt = createdAt)

            Then("[U-06] cold-start로 판정된다 (베이스라인 미적용)") {
                result shouldBe true
            }
        }
    }

    Given("[U-07] cold-start 경계값 — 토큰 생성 정확히 14일") {
        val createdAt = ZonedDateTime.now().minusDays(14)

        When("isColdStart를 호출하면") {
            val result = detector.isColdStart(tokenCreatedAt = createdAt)

            Then("[U-07] cold-start가 아니다 (14일 경과 — 학습 완료)") {
                result shouldBe false
            }
        }
    }

    Given("[U-08] cold-start 판정 — 토큰 생성 후 15일 경과") {
        val createdAt = ZonedDateTime.now().minusDays(15)

        When("isColdStart를 호출하면") {
            val result = detector.isColdStart(tokenCreatedAt = createdAt)

            Then("[U-08] cold-start가 아니다") {
                result shouldBe false
            }
        }
    }

    Given("[U-09] 베이스라인 평균 계산 — 7일치 시간대별 통계") {
        val dailyCounts = listOf(10L, 12L, 8L, 11L, 9L, 13L, 7L)

        When("computeBaselineAverage를 호출하면") {
            val result = detector.computeBaselineAverage(dailyCounts)

            Then("[U-09] 평균값이 반환된다") {
                result shouldBe dailyCounts.average()
            }
        }
    }

    Given("[U-10] 베이스라인 평균 계산 — 빈 리스트") {
        When("computeBaselineAverage를 빈 리스트로 호출하면") {
            val result = detector.computeBaselineAverage(emptyList())

            Then("[U-10] 0.0이 반환된다") {
                result shouldBe 0.0
            }
        }
    }
})
