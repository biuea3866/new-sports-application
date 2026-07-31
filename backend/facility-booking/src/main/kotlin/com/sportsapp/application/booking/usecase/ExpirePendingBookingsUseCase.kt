package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.config.BookingExpiryProperties
import com.sportsapp.application.booking.dto.BookingExpiryResult
import com.sportsapp.domain.booking.dto.BookingExpiryCandidate
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.service.PaymentDomainService
import org.springframework.stereotype.Service

/**
 * W1-11c — booking PENDING 예약 만료 스위퍼 (4차 재설계).
 *
 * 청크 단위(bookingExpiryProperties.chunkSize)로 PENDING 후보(createdAt 포함)를 조회 →
 * payment에게 "결제가 시작이라도 됐는가"(live)·"돈을 받았는가"(settled)라는 **사실**만
 * 물어([PaymentDomainService.findPaymentLiveness]) → booking 자신의 정책(빠른/느린 TTL)으로
 * 최종 만료 대상을 판정([BookingDomainService.filterExpirable]) → 나머지만
 * [ExpireBookingChunkUseCase]가 청크 단위 독립 트랜잭션으로 커밋한다. 한 주기 상한
 * (maxChunksPerRun)만큼만 청크를 처리한다.
 *
 * **payment는 시간 창을 갖지 않는다** — 3차 설계(activeWindowMinutes)는 `initiatePg`가 주문
 * 생성 요청 트랜잭션 안에서 끝나 사용자가 결제창에 머무는 동안 payment 행 쓰기가 0건이라
 * `updatedAt`이 신호가 되지 못해 폐기됐다. 대신 payment 상태 자체(live/settled)만 받고, TTL
 * 정책은 이 UseCase가 [BookingExpiryProperties]에서 읽어 booking DomainService에 넘긴다.
 * 크로스 컨텍스트 조합은 이 application 레이어에서만 수행하고, 각 DomainService는 서로의
 * 타입을 모른다(payment는 Set<Long>만, booking은 그 Set<Long>만 받는다).
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
        val candidates = bookingDomainService.findExpirableBookingCandidates(
            bookingExpiryProperties.ttlMinutes,
            afterId,
            bookingExpiryProperties.chunkSize,
        )
        if (candidates.isEmpty()) return accumulated
        val chunkResult = processCandidates(candidates)
        return processChunks(afterId = candidates.last().bookingId, chunksLeft = chunksLeft - 1, accumulated = accumulated + chunkResult)
    }

    private fun processCandidates(candidates: List<BookingExpiryCandidate>): BookingExpiryResult {
        val candidateIds = candidates.map { it.bookingId }
        val liveness = paymentDomainService.findPaymentLiveness(OrderType.BOOKING, candidateIds)
        val expirableIds = bookingDomainService.filterExpirable(
            candidates,
            liveness.liveOrderIds,
            liveness.settledOrderIds,
            bookingExpiryProperties.readyTtlMinutes,
        )
        val expiredCount = expireBookingChunkUseCase.execute(expirableIds)
        return BookingExpiryResult(expiredCount = expiredCount, skippedCount = candidateIds.size - expirableIds.size)
    }
}
