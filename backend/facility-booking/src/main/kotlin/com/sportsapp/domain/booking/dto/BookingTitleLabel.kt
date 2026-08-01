package com.sportsapp.domain.booking.dto

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * booking 컨텍스트 자기 데이터(Slot.date·Slot.timeRange)로 구성하는 서술형 예약 라벨.
 *
 * [BookingOrderItem]과 booking 단건 상세 조회가 함께 사용한다. 시설/프로그램의 사람이 읽는
 * 이름은 facility 컨텍스트에 있어 조회 시 참조하면 도메인 교차 위반이므로, Slot 부재·삭제
 * 시에는 [DEFAULT_TITLE]로 방어 반환한다.
 */
object BookingTitleLabel {
    const val DEFAULT_TITLE = "시설 예약"
    // 사용자에게 그대로 보이는 문자열이라 앱의 한국어 날짜 표기(2026. 08. 10.)를 따른다 —
    // ISO 하이픈은 주문 내역에서 예약 항목만 다른 형식으로 튀어 보인다(캡쳐 13-주문-내역).
    private val SLOT_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy. MM. dd.")

    fun of(slotDate: ZonedDateTime?, slotTimeRange: String?): String {
        if (slotDate == null || slotTimeRange == null) return DEFAULT_TITLE
        return "${slotDate.format(SLOT_DATE_FORMATTER)} $slotTimeRange $DEFAULT_TITLE"
    }
}
