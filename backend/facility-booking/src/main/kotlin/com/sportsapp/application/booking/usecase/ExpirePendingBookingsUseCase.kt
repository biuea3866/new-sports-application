package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.config.BookingExpiryProperties
import com.sportsapp.application.booking.dto.BookingExpiryResult
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.service.PaymentDomainService
import org.springframework.stereotype.Service

/**
 * W1-11c — booking PENDING 예약 만료 스위퍼.
 *
 * 청크 단위(bookingExpiryProperties.chunkSize)로 PENDING 후보를 조회 → payment(C1) 만료 금지
 * 가드(findUnexpirableOrderIds — PENDING/READY/COMPLETED/REFUNDED는 만료 금지)를 확인해
 * 만료 금지 대상은 건너뛴다 → 나머지만 [ExpireBookingChunkUseCase]가 청크 단위 독립 트랜잭션으로
 * 커밋한다. 한 주기 상한(maxChunksPerRun)만큼만 청크를 처리한다. 크로스 컨텍스트 조합은 이
 * application 레이어에서만 수행하고, 각 DomainService는 서로의 타입을 모른다.
 *
 * **청크 커서(afterId)**: 건너뛴(결제 진행 중) 예약은 다음 청크 조회에서 id > afterId 조건으로
 * 제외된다 — 커서가 없으면 같은 건이 매 청크 재조회되어 ① 상한(maxChunksPerRun)만큼 청크를
 * 다 써도 스위퍼가 진행하지 못하는 head-of-line blocking, ② skippedCount가 청크 수만큼
 * 중복 집계되는 문제가 생긴다. 커서는 이번 실행(execute 1회) 안에서만 유효하고, 다음 스케줄
 * 주기는 afterId=0부터 다시 전체 후보를 훑는다(그사이 결제가 종료됐을 수 있으므로).
 */
@Service
class ExpirePendingBookingsUseCase(
    private val bookingDomainService: BookingDomainService,
    private val paymentDomainService: PaymentDomainService,
    private val expireBookingChunkUseCase: ExpireBookingChunkUseCase,
    private val bookingExpiryProperties: BookingExpiryProperties,
) {
    fun execute(): BookingExpiryResult =
        processChunks(afterId = 0L, chunksLeft = bookingExpiryProperties.maxChunksPerRun, accumulated = BookingExpiryResult.empty())

    private tailrec fun processChunks(afterId: Long, chunksLeft: Int, accumulated: BookingExpiryResult): BookingExpiryResult {
        if (chunksLeft <= 0) return accumulated
        val candidateIds = bookingDomainService.findExpirableBookingIds(
            bookingExpiryProperties.ttlMinutes,
            afterId,
            bookingExpiryProperties.chunkSize,
        )
        if (candidateIds.isEmpty()) return accumulated
        val chunkResult = processCandidates(candidateIds)
        return processChunks(afterId = candidateIds.last(), chunksLeft = chunksLeft - 1, accumulated = accumulated + chunkResult)
    }

    private fun processCandidates(candidateIds: List<Long>): BookingExpiryResult {
        val unexpirableOrderIds = paymentDomainService.findUnexpirableOrderIds(OrderType.BOOKING, candidateIds)
        val expiredCount = expireBookingChunkUseCase.execute(candidateIds - unexpirableOrderIds)
        return BookingExpiryResult(expiredCount = expiredCount, skippedCount = unexpirableOrderIds.size)
    }
}
