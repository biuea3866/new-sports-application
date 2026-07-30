package com.sportsapp.application.booking.usecase

import com.sportsapp.domain.booking.service.BookingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 취소 이벤트를 받아 자기 PENDING 예약을 취소한다.
 * cancelPending 은 이미 CANCELLED 인 예약을 조용히 반환하므로 중복 수신에 멱등하다.
 */
@Service
class CancelBookingPaymentUseCase(
    private val bookingDomainService: BookingDomainService,
) {
    @Transactional
    fun execute(orderId: Long) {
        bookingDomainService.cancelPending(orderId)
    }
}
