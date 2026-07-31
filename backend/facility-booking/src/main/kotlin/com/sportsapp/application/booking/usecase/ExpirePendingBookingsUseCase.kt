package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.config.BookingExpiryProperties
import com.sportsapp.application.booking.dto.BookingExpiryResult
import com.sportsapp.application.payment.config.PaymentExpiryGuardProperties
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.service.PaymentDomainService
import org.springframework.stereotype.Service

/**
 * W1-11c — booking PENDING 예약 만료 스위퍼.
 *
 * 청크 단위(bookingExpiryProperties.chunkSize)로 PENDING 후보를 조회 → payment(C1) 만료 금지
 * 가드(findUnexpirableOrderIds — status만이 아니라 updatedAt까지 함께 보아 "방치된 PENDING/
 * READY"와 "결제 진행 중인 PENDING/READY"를 구분한다. 판정 규칙은
 * [com.sportsapp.domain.payment.service.PaymentExpiryGuard] 참고)를 확인해 만료 금지
 * 대상은 건너뛴다 → 나머지만 [ExpireBookingChunkUseCase]가 청크 단위 독립 트랜잭션으로
 * 커밋한다. 한 주기 상한(maxChunksPerRun)만큼만 청크를 처리한다. 크로스 컨텍스트 조합은 이
 * application 레이어에서만 수행하고, 각 DomainService는 서로의 타입을 모른다.
 *
 * **청크 커서(afterId)**: 건너뛴(결제 진행 중) 예약은 다음 청크 조회에서 id > afterId 조건으로
 * 제외된다 — 커서가 없으면 같은 건이 매 청크 재조회되어 ① 상한(maxChunksPerRun)만큼 청크를
 * 다 써도 스위퍼가 진행하지 못하는 head-of-line blocking, ② skippedCount가 청크 수만큼
 * 중복 집계되는 문제가 생긴다. 커서는 이번 실행(execute 1회) 안에서만 유효하고, 다음 스케줄
 * 주기는 afterId=0부터 다시 전체 후보를 훑는다(그사이 결제가 종료됐을 수 있으므로).
 *
 * **activeWindowMinutes**(payment.expiry-guard.active-window-minutes, 기본 5분)는 반드시
 * booking.expiry.ttl-minutes(기본 15분)보다 짧아야 한다 — 같거나 길면 모든 PENDING 예약이
 * 항상 만료 금지로 판정돼 스위퍼가 무력화된다.
 */
@Service
class ExpirePendingBookingsUseCase(
    private val bookingDomainService: BookingDomainService,
    private val paymentDomainService: PaymentDomainService,
    private val expireBookingChunkUseCase: ExpireBookingChunkUseCase,
    private val bookingExpiryProperties: BookingExpiryProperties,
    private val paymentExpiryGuardProperties: PaymentExpiryGuardProperties,
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
        val unexpirableOrderIds = paymentDomainService.findUnexpirableOrderIds(
            OrderType.BOOKING,
            candidateIds,
            paymentExpiryGuardProperties.activeWindowMinutes,
        )
        val expiredCount = expireBookingChunkUseCase.execute(candidateIds - unexpirableOrderIds)
        return BookingExpiryResult(expiredCount = expiredCount, skippedCount = unexpirableOrderIds.size)
    }
}
