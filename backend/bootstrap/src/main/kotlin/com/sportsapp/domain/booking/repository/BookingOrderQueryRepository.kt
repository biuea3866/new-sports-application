package com.sportsapp.domain.booking.repository

import com.sportsapp.domain.booking.dto.BookingOrderItem

/**
 * order 통합 조회(BE-08)가 소비하는 사용자별 Booking 읽기 전용 조회.
 * bookings → slots(둘 다 booking 컨텍스트) 조인으로 title 라벨을 구성해 반환한다.
 */
interface BookingOrderQueryRepository {
    fun findByUserId(userId: Long): List<BookingOrderItem>
}
