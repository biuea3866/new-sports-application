package com.sportsapp.application.booking.usecase

import com.sportsapp.domain.booking.service.BookingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 확정 이벤트를 받아 자기 예약을 CONFIRMED 로 전이한다.
 * confirmBooking 은 이미 CONFIRMED 인 예약을 조용히 반환하므로 중복 수신에 멱등하다.
 */
@Service
class ConfirmBookingPaymentUseCase(
    private val bookingDomainService: BookingDomainService,
) {
    @Transactional
    fun execute(orderId: Long, paymentId: Long) {
        // named argument 강제(6차 재리뷰 p3) — 인접한 동일 타입(Long) 위치 인자 뒤바뀜 방지.
        bookingDomainService.confirmBooking(bookingId = orderId, paymentId = paymentId)
    }
}
