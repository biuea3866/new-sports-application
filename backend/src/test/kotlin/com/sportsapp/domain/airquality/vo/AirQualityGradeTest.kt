package com.sportsapp.domain.airquality.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class AirQualityGradeTest : BehaviorSpec({

    Given("PM10 30(경계 하한)") {
        Then("GOOD으로 등급화된다") {
            AirQualityGrade.ofPm10(30) shouldBe AirQualityGrade.GOOD
        }
    }

    Given("PM10 31(경계 상한)") {
        Then("MODERATE로 등급화된다") {
            AirQualityGrade.ofPm10(31) shouldBe AirQualityGrade.MODERATE
        }
    }

    Given("PM10 80(경계 하한)") {
        Then("MODERATE로 등급화된다") {
            AirQualityGrade.ofPm10(80) shouldBe AirQualityGrade.MODERATE
        }
    }

    Given("PM10 81(경계 상한)") {
        Then("BAD로 등급화된다") {
            AirQualityGrade.ofPm10(81) shouldBe AirQualityGrade.BAD
        }
    }

    Given("PM10 150(경계 하한)") {
        Then("BAD로 등급화된다") {
            AirQualityGrade.ofPm10(150) shouldBe AirQualityGrade.BAD
        }
    }

    Given("PM10 151(경계 상한)") {
        Then("VERY_BAD로 등급화된다") {
            AirQualityGrade.ofPm10(151) shouldBe AirQualityGrade.VERY_BAD
        }
    }

    Given("PM10 값이 null") {
        Then("UNKNOWN으로 등급화된다") {
            AirQualityGrade.ofPm10(null) shouldBe AirQualityGrade.UNKNOWN
        }
    }

    Given("PM2.5 15(경계 하한)") {
        Then("GOOD으로 등급화된다") {
            AirQualityGrade.ofPm25(15) shouldBe AirQualityGrade.GOOD
        }
    }

    Given("PM2.5 16(경계 상한)") {
        Then("MODERATE로 등급화된다") {
            AirQualityGrade.ofPm25(16) shouldBe AirQualityGrade.MODERATE
        }
    }

    Given("PM2.5 35(경계 하한)") {
        Then("MODERATE로 등급화된다") {
            AirQualityGrade.ofPm25(35) shouldBe AirQualityGrade.MODERATE
        }
    }

    Given("PM2.5 36(경계 상한)") {
        Then("BAD로 등급화된다") {
            AirQualityGrade.ofPm25(36) shouldBe AirQualityGrade.BAD
        }
    }

    Given("PM2.5 75(경계 하한)") {
        Then("BAD로 등급화된다") {
            AirQualityGrade.ofPm25(75) shouldBe AirQualityGrade.BAD
        }
    }

    Given("PM2.5 76(경계 상한)") {
        Then("VERY_BAD로 등급화된다") {
            AirQualityGrade.ofPm25(76) shouldBe AirQualityGrade.VERY_BAD
        }
    }

    Given("PM2.5 값이 null") {
        Then("UNKNOWN으로 등급화된다") {
            AirQualityGrade.ofPm25(null) shouldBe AirQualityGrade.UNKNOWN
        }
    }

    Given("BAD와 MODERATE 조합") {
        Then("더 나쁜 등급인 BAD를 반환한다") {
            AirQualityGrade.worseOf(AirQualityGrade.BAD, AirQualityGrade.MODERATE) shouldBe AirQualityGrade.BAD
            AirQualityGrade.worseOf(AirQualityGrade.MODERATE, AirQualityGrade.BAD) shouldBe AirQualityGrade.BAD
        }
    }

    Given("VERY_BAD와 BAD 조합") {
        Then("더 나쁜 등급인 VERY_BAD를 반환한다") {
            AirQualityGrade.worseOf(AirQualityGrade.VERY_BAD, AirQualityGrade.BAD) shouldBe AirQualityGrade.VERY_BAD
        }
    }

    Given("등급 하나가 UNKNOWN이고 다른 하나가 알려진 등급일 때") {
        Then("UNKNOWN을 제외하고 알려진 등급을 반환한다") {
            AirQualityGrade.worseOf(AirQualityGrade.UNKNOWN, AirQualityGrade.GOOD) shouldBe AirQualityGrade.GOOD
            AirQualityGrade.worseOf(AirQualityGrade.BAD, AirQualityGrade.UNKNOWN) shouldBe AirQualityGrade.BAD
        }
    }

    Given("두 등급 모두 UNKNOWN일 때") {
        Then("UNKNOWN을 반환한다") {
            AirQualityGrade.worseOf(AirQualityGrade.UNKNOWN, AirQualityGrade.UNKNOWN) shouldBe AirQualityGrade.UNKNOWN
        }
    }

    Given("BAD 등급") {
        Then("isBadOrWorse는 true를 반환한다") {
            AirQualityGrade.BAD.isBadOrWorse() shouldBe true
        }
    }

    Given("VERY_BAD 등급") {
        Then("isBadOrWorse는 true를 반환한다") {
            AirQualityGrade.VERY_BAD.isBadOrWorse() shouldBe true
        }
    }

    Given("MODERATE 등급") {
        Then("isBadOrWorse는 false를 반환한다") {
            AirQualityGrade.MODERATE.isBadOrWorse() shouldBe false
        }
    }

    Given("GOOD 등급") {
        Then("isBadOrWorse는 false를 반환한다") {
            AirQualityGrade.GOOD.isBadOrWorse() shouldBe false
        }
    }

    Given("UNKNOWN 등급") {
        Then("isBadOrWorse는 false를 반환한다") {
            AirQualityGrade.UNKNOWN.isBadOrWorse() shouldBe false
        }
    }
})
