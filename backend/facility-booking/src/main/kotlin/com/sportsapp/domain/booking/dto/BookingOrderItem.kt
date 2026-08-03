package com.sportsapp.domain.booking.dto

import com.sportsapp.domain.booking.entity.BookingStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * order 통합 조회가 소비하는 사용자별 Booking 읽기 프로젝션.
 *
 * title은 booking 컨텍스트 자기 데이터(Slot.date·Slot.timeRange)로 구성한 서술형 라벨이다.
 * 시설/프로그램의 사람이 읽는 이름은 facility 컨텍스트에 있어 조회 시 참조하면 R1(도메인 교차)
 * 위반이므로, Slot 부재·삭제 시에는 [BookingTitleLabel.DEFAULT_TITLE]로 방어 반환한다.
 * 라벨 구성 로직은 [BookingTitleLabel]로 추출해 단건 상세([BookingDetail])와 공유한다.
 *
 * [amount]는 [com.sportsapp.domain.booking.entity.Booking.amount] 그대로다 — booking 자기
 * 컬럼(V65)이라 payment 역참조 없이 노출한다. 이 컬럼 도입 이전(과거) 예약은 저장 이력이 없어
 * `null`(금액 확정 불가)이다.
 */
data class BookingOrderItem(
    val bookingId: Long,
    val slotId: Long,
    val userId: Long,
    val status: BookingStatus,
    val paymentId: Long?,
    val title: String,
    val createdAt: ZonedDateTime,
    val amount: BigDecimal? = null,
) {
    /**
     * 라벨 구성에 필요한 Slot 파생 정보를 묶은 값객체 (LongParameterList 회피).
     * Slot 부재·삭제 시 둘 다 null일 수 있다.
     */
    data class SlotLabelSource(
        val date: ZonedDateTime?,
        val timeRange: String?,
    )

    companion object {
        fun of(
            bookingId: Long,
            slotId: Long,
            userId: Long,
            status: BookingStatus,
            paymentId: Long?,
            createdAt: ZonedDateTime,
            slotLabelSource: SlotLabelSource,
            amount: BigDecimal? = null,
        ): BookingOrderItem = BookingOrderItem(
            bookingId = bookingId,
            slotId = slotId,
            userId = userId,
            status = status,
            paymentId = paymentId,
            title = BookingTitleLabel.of(slotLabelSource.date, slotLabelSource.timeRange),
            createdAt = createdAt,
            amount = amount,
        )
    }
}
