package com.sportsapp.infrastructure.airquality.gateway

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 그리드키 산출(레디스 키 계약 §1) 라운딩 규칙 검증.
 * BigDecimal.setScale(3, HALF_UP) + Locale.ROOT 포맷으로 소수 3자리 격자 키를 만든다.
 */
class AirQualityGridKeyTest : BehaviorSpec({

    Given("서울시청 좌표(37.5665, 126.9780)가 주어지면") {
        When("gridKey 를 산출하면") {
            Then("소수 3자리로 반올림된 37.567_126.978 을 반환한다") {
                AirQualityGridKey.of(37.5665, 126.9780) shouldBe "37.567_126.978"
            }
        }
    }

    Given("해운대 좌표(35.1587, 129.1604)가 주어지면") {
        When("gridKey 를 산출하면") {
            Then("35.159_129.160 을 반환한다(trailing zero 보존)") {
                AirQualityGridKey.of(35.1587, 129.1604) shouldBe "35.159_129.160"
            }
        }
    }

    Given("소수 4자리가 반올림 경계 미만인 좌표(37.5003, 127.0002)가 주어지면") {
        When("gridKey 를 산출하면") {
            Then("37.500_127.000 을 반환한다") {
                AirQualityGridKey.of(37.5003, 127.0002) shouldBe "37.500_127.000"
            }
        }
    }
})
