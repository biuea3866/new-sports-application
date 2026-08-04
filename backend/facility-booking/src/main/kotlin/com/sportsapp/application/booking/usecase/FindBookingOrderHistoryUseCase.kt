package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.dto.InternalBookingOrderHistoryItemResponse
import com.sportsapp.domain.booking.service.BookingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * edge 통합 주문내역(BE-08)의 `OrderHistoryGateway.findBookingOrders` 원격 구현(2단계) 공급자
 * (S2-04, `GET /internal/order-history/bookings`). [BookingDomainService.findOrderHistory]는
 * 요청한 userId 소유 예약만 MySQL 소유 테이블(bookings)에서 조회해 MongoDB에 접근하지 않는다.
 */
@Service
class FindBookingOrderHistoryUseCase(
    private val bookingDomainService: BookingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long): List<InternalBookingOrderHistoryItemResponse> =
        bookingDomainService.findOrderHistory(userId).map { InternalBookingOrderHistoryItemResponse.of(it) }
}
