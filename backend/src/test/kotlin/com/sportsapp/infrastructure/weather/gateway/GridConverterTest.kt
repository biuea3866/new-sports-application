package com.sportsapp.infrastructure.weather

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZoneId
import java.time.ZonedDateTime

class GridConverterTest : BehaviorSpec({

    Given("서울시청 좌표(37.5665, 126.9780)") {
        When("[U-01] 격자로 변환하면") {
            val grid = GridConverter.toGrid(lat = 37.5665, lng = 126.9780)

            Then("기상청 격자 (60, 127) 이 된다") {
                grid.nx shouldBe 60
                grid.ny shouldBe 127
            }
        }
    }

    Given("부산 좌표(35.1796, 129.0756)") {
        When("[U-02] 격자로 변환하면") {
            val grid = GridConverter.toGrid(lat = 35.1796, lng = 129.0756)

            Then("기상청 격자 (98, 76) 이 된다") {
                grid.nx shouldBe 98
                grid.ny shouldBe 76
            }
        }
    }
})

class BaseTimeResolverTest : BehaviorSpec({

    val seoul = ZoneId.of("Asia/Seoul")

    Given("오후 3시 30분") {
        val now = ZonedDateTime.of(2026, 5, 30, 15, 30, 0, 0, seoul)
        When("[U-03] base 시각을 계산하면") {
            val base = BaseTimeResolver.resolve(now)
            Then("당일 1400 발표를 사용한다") {
                base.baseDate shouldBe "20260530"
                base.baseTime shouldBe "1400"
            }
        }
    }

    Given("새벽 1시(첫 발표 02시 이전)") {
        val now = ZonedDateTime.of(2026, 5, 30, 1, 0, 0, 0, seoul)
        When("[U-04] base 시각을 계산하면") {
            val base = BaseTimeResolver.resolve(now)
            Then("전일 2300 발표를 사용한다") {
                base.baseDate shouldBe "20260529"
                base.baseTime shouldBe "2300"
            }
        }
    }
})
