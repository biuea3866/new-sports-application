package com.sportsapp.infrastructure.airquality.gateway

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.shouldBeBetween

/**
 * WGS84(EPSG:4326) → 에어코리아 TM 좌표(EPSG:5181) 변환 검증.
 *
 * golden 값은 실서버 실측(2026-07-06)으로 EPSG:5181 을 확정한 근거다:
 * 1) getMsrstnList?addr=서울 중구 → 측정소 "중구" 위경도(dmX=37.564639, dmY=126.975961).
 * 2) 그 위경도를 EPSG:5181 정의로 변환한 tmX/tmY 로 실서버 getNearbyMsrstnList 를 호출하면
 *    최근접 측정소가 "중구" 자신(tm=0.3km)으로 돌아와 EPSG:5181 이 정답임을 확인했다.
 * 3) 부산 해운대구 "좌동"(dmX=35.1708927, dmY=129.1741659) 좌표로도 동일하게 재현했다
 *    (최근접 측정소 "좌동", tm=0.3km).
 */
class AirKoreaTmProjectionTest : BehaviorSpec({

    Given("서울 중구 측정소 위경도(37.564639, 126.975961)가 주어지면") {
        When("에어코리아 TM 좌표로 변환하면") {
            Then("실서버 실측으로 확정된 EPSG:5181 근사값(tmX≈197876.17, tmY≈451678.53)을 ±1m 오차로 반환한다") {
                val coordinate = AirKoreaTmProjection.toTm(lat = 37.564639, lng = 126.975961)

                coordinate.tmX.toDouble().shouldBeBetween(197875.17, 197877.17, 0.0)
                coordinate.tmY.toDouble().shouldBeBetween(451677.53, 451679.53, 0.0)
            }
        }
    }

    Given("부산 해운대구 좌동 측정소 위경도(35.1708927, 129.1741659)가 주어지면") {
        When("에어코리아 TM 좌표로 변환하면") {
            Then("실서버 실측으로 확정된 EPSG:5181 근사값(tmX≈398078.23, tmY≈188219.47)을 ±1m 오차로 반환한다") {
                val coordinate = AirKoreaTmProjection.toTm(lat = 35.1708927, lng = 129.1741659)

                coordinate.tmX.toDouble().shouldBeBetween(398077.23, 398079.23, 0.0)
                coordinate.tmY.toDouble().shouldBeBetween(188218.47, 188220.47, 0.0)
            }
        }
    }
})
