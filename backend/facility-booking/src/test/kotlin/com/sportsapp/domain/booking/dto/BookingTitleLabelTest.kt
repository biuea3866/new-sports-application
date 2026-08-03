package com.sportsapp.domain.booking.dto

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * 예약 주문 라벨의 날짜 표기 형식.
 *
 * 회귀 배경: 주문 내역에서 예약 항목만 ISO 하이픈(`2026-07-31 19:00-20:00 시설 예약`)으로
 * 표기돼, 같은 화면의 다른 항목이 쓰는 한국어 날짜 표기(`2026. 08. 10.`)와 어긋났다
 * (유즈케이스 캡쳐 13-주문-내역). 사용자에게 보이는 문자열이므로 앱 표기 관례를 따른다.
 */
class BookingTitleLabelTest : BehaviorSpec({

    Given("슬롯 날짜와 시간대가 있는 예약") {
        val slotDate = ZonedDateTime.of(2026, 7, 31, 0, 0, 0, 0, ZoneOffset.UTC)

        When("라벨을 구성하면") {
            val label = BookingTitleLabel.of(slotDate, "19:00-20:00")

            Then("날짜를 한국어 표기(yyyy. MM. dd.)로 쓴다") {
                label shouldBe "2026. 07. 31. 19:00-20:00 시설 예약"
            }

            Then("ISO 하이픈 날짜를 쓰지 않는다") {
                label.contains("2026-07-31") shouldBe false
            }
        }
    }

    Given("한 자리 월·일인 예약") {
        val slotDate = ZonedDateTime.of(2026, 8, 10, 0, 0, 0, 0, ZoneOffset.UTC)

        When("라벨을 구성하면") {
            val label = BookingTitleLabel.of(slotDate, "07:00-08:00")

            Then("월·일을 두 자리로 zero-pad 한다") {
                label shouldBe "2026. 08. 10. 07:00-08:00 시설 예약"
            }
        }
    }

    Given("슬롯이 삭제돼 날짜·시간대를 알 수 없는 예약") {
        When("라벨을 구성하면") {
            Then("기본 라벨로 방어 반환한다") {
                BookingTitleLabel.of(null, "19:00-20:00") shouldBe BookingTitleLabel.DEFAULT_TITLE
                BookingTitleLabel.of(
                    ZonedDateTime.of(2026, 7, 31, 0, 0, 0, 0, ZoneOffset.UTC),
                    null,
                ) shouldBe BookingTitleLabel.DEFAULT_TITLE
            }
        }
    }
})
