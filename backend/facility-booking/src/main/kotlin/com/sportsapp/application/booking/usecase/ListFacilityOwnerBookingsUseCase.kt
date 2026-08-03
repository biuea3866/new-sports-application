package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.dto.GetBookingResult
import com.sportsapp.application.booking.dto.ListBookingsResult
import com.sportsapp.application.booking.dto.ListFacilityOwnerBookingsCommand
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.payment.service.PaymentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 포털 "예약 관리" — 파트너가 소유한 시설의 예약 목록.
 *
 * 결제 상태는 공용 payment 컨텍스트에서 조회한다(주문 컨텍스트 → 공용 컨텍스트 방향이라
 * 역참조가 아니다). 기존 [ListMyBookingsUseCase]와 같은 조합 방식이고 조회 스코프만 다르다.
 */
@Service
class ListFacilityOwnerBookingsUseCase(
    private val bookingDomainService: BookingDomainService,
    private val paymentDomainService: PaymentDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: ListFacilityOwnerBookingsCommand): ListBookingsResult {
        val bookingPage = bookingDomainService.findBookingsForFacilityOwner(
            ownerUserId = command.ownerUserId,
            status = command.status,
            pageable = command.pageable,
        )
        val paymentStatuses = paymentDomainService.findStatuses(bookingPage.content.mapNotNull { it.paymentId })
        return ListBookingsResult.of(
            bookingPage.map { booking -> GetBookingResult.of(booking, paymentStatuses[booking.paymentId]) }
        )
    }
}
