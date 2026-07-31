package com.sportsapp.application.booking.usecase

import com.sportsapp.domain.booking.service.BookingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * W1-11c 만료 스위퍼의 청크 단위 원자적 커밋 경계.
 *
 * `@Transactional`은 UseCase에 선언한다는 컨벤션에 맞춰, 청크 커밋 트랜잭션을 DomainService가
 * 아니라 이 UseCase가 소유한다. [ExpirePendingBookingsUseCase]가 청크마다 이 UseCase를 별도
 * 호출하므로 별도 빈 경계로 self-invocation(같은 클래스 내부 호출 시 `@Transactional` 프록시가
 * 적용되지 않는 문제) 없이 청크별 독립 트랜잭션이 보장된다.
 */
@Service
class ExpireBookingChunkUseCase(
    private val bookingDomainService: BookingDomainService,
) {
    @Transactional
    fun execute(bookingIds: List<Long>): Int = bookingDomainService.expireBookings(bookingIds)
}
