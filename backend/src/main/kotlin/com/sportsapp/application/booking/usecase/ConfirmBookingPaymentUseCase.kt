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
        bookingDomainService.confirmBooking(orderId, paymentId)
    }
}
