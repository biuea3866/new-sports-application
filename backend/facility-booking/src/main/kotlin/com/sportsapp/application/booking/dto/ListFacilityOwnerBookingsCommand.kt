package com.sportsapp.application.booking.dto

import com.sportsapp.domain.booking.entity.BookingStatus
import org.springframework.data.domain.Pageable

/**
 * 파트너(시설 소유자) 스코프 예약 조회 파라미터.
 *
 * [ListBookingsCommand]의 `userId`가 **예약자**인 것과 달리 [ownerUserId]는 **시설 소유자**다.
 * 두 값을 혼동하면 조회 범위가 통째로 바뀌므로 커맨드 타입을 분리한다.
 */
data class ListFacilityOwnerBookingsCommand(
    val ownerUserId: Long,
    val status: BookingStatus?,
    val pageable: Pageable,
)
