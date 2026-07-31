package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.config.BookingExpiryProperties
import com.sportsapp.application.booking.dto.BookingExpiryResult
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.service.PaymentDomainService
import java.time.ZonedDateTime
import org.springframework.stereotype.Service

/**
 * W1-11c — booking PENDING 예약 만료 스위퍼.
 *
 * 청크 단위(bookingExpiryProperties.chunkSize)로 PENDING 후보를 조회 → payment(C1) 성공 여부를
 * 확인해 결제 성공 건은 건너뛴다 → 나머지만 BookingDomainService.expireBookings로 청크 커밋한다.
 * 한 주기 상한(maxChunksPerRun)만큼만 청크를 처리한다. 크로스 컨텍스트 조합은 이 application
 * 레이어에서만 수행하고, 각 DomainService는 서로의 타입을 모른다.
 */
@Service
class ExpirePendingBookingsUseCase(
    private val bookingDomainService: BookingDomainService,
    private val paymentDomainService: PaymentDomainService,
    private val bookingExpiryProperties: BookingExpiryProperties,
) {
    fun execute(): BookingExpiryResult =
        generateSequence { processChunk() }
            .take(bookingExpiryProperties.maxChunksPerRun)
            .fold(BookingExpiryResult.empty()) { total, chunk -> total + chunk }

    private fun processChunk(): BookingExpiryResult? {
        val threshold = ZonedDateTime.now().minusMinutes(bookingExpiryProperties.ttlMinutes)
        val candidateIds = bookingDomainService.findExpirableBookingIds(threshold, bookingExpiryProperties.chunkSize)
        if (candidateIds.isEmpty()) return null
        val paidOrderIds = paymentDomainService.findCompletedOrderIds(OrderType.BOOKING, candidateIds)
        val expiredCount = bookingDomainService.expireBookings(candidateIds - paidOrderIds)
        return BookingExpiryResult(expiredCount = expiredCount, skippedCount = paidOrderIds.size)
    }
}
