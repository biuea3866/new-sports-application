package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.config.BookingExpiryProperties
import com.sportsapp.application.booking.dto.BookingExpiryResult
import com.sportsapp.domain.booking.dto.BookingExpiryCandidate
import com.sportsapp.domain.booking.dto.BookingExpiryTtlPolicy
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.service.PaymentDomainService
import org.springframework.stereotype.Service

/**
 * W1-11c — booking PENDING 예약 만료 스위퍼 (6차 재설계).
 *
 * 청크 단위(bookingExpiryProperties.chunkSize)로 PENDING 후보(createdAt 포함)를 조회 →
 * payment에게 orderId별 결제 생존 판정([OrderPaymentLiveness] — domain.common 공유 커널)을
 * 물어([PaymentDomainService.findPaymentLiveness]) → booking 자신의 정책(빠른/느린 TTL)으로
 * 최종 만료 대상을 판정([BookingDomainService.filterExpirable]) → 나머지만
 * [ExpireBookingChunkUseCase]가 청크 단위 독립 트랜잭션으로 커밋한다. 한 주기 상한
 * (maxChunksPerRun)만큼만 청크를 처리한다.
 *
 * **payment는 시간 창(TTL 분값)을 갖지 않는다** — 3차 설계(activeWindowMinutes)는
 * `initiatePg`가 주문 생성 요청 트랜잭션 안에서 끝나 사용자가 결제창에 머무는 동안 payment
 * 행 쓰기가 0건이라 `updatedAt`이 신호가 되지 못해 폐기됐다. 대신 payment 상태 자체
 * (settled/live/attempting/none)만 받고, TTL 분값은 이 UseCase가 [BookingExpiryProperties]에서
 * 읽어 [BookingExpiryTtlPolicy]로 묶어 booking DomainService에 넘긴다. 크로스 컨텍스트 조합은
 * 이 application 레이어에서만 수행한다 — payment의 `PaymentLivenessQueryResult`(payment
 * 전용 dto)는 이 UseCase까지만 오고, booking에는 `livenessByOrderId`(domain.common 공유
 * 커널 타입의 맵)만 넘긴다.
 *
 * **명명 인자 강제**: [BookingDomainService.findExpirableBookingCandidates]의
 * `ttlMinutes`(Long)와 `afterId`(Long)는 인접한 동일 타입이라 위치 인자로 바꿔 넘겨도
 * 컴파일이 통과해 TTL↔커서가 뒤바뀌는 오동작이 조용히 재발할 수 있어 named argument로
 * 호출한다.
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

    // guard clause 2개(early return) + 재귀 종료 반환 = 3건. 컨벤션이 권장하는 guard clause의
    // 부산물이라 억제한다 (private-be-code-convention "Guard clause 적극 사용").
    @Suppress("ReturnCount")
    private tailrec fun processChunks(afterId: Long, chunksLeft: Int, accumulated: BookingExpiryResult): BookingExpiryResult {
        if (chunksLeft <= 0) return accumulated
        val candidates = bookingDomainService.findExpirableBookingCandidates(
            ttlMinutes = bookingExpiryProperties.ttlMinutes,
            afterId = afterId,
            limit = bookingExpiryProperties.chunkSize,
        )
        if (candidates.isEmpty()) return accumulated
        val chunkResult = processCandidates(candidates)
        return processChunks(afterId = candidates.last().bookingId, chunksLeft = chunksLeft - 1, accumulated = accumulated + chunkResult)
    }

    private fun processCandidates(candidates: List<BookingExpiryCandidate>): BookingExpiryResult {
        val candidateIds = candidates.map { it.bookingId }
        val liveness = paymentDomainService.findPaymentLiveness(orderType = OrderType.BOOKING, orderIds = candidateIds)
        val filterResult = bookingDomainService.filterExpirable(
            candidates = candidates,
            liveness = liveness.livenessByOrderId,
            ttlPolicy = BookingExpiryTtlPolicy(
                ttlMinutes = bookingExpiryProperties.ttlMinutes,
                readyTtlMinutes = bookingExpiryProperties.readyTtlMinutes,
            ),
        )
        val expiredCount = expireBookingChunkUseCase.execute(filterResult.expirableIds)
        // CAS(tryExpire)에서 경합에 진 건 — expirableIds로 판정됐으나 실제 전이는 실패한 건수.
        // expiredCount + skippedCount(=candidateIds - expirableIds) 만으로는 이 경합을
        // 관측할 수 없어 별도 카운터로 분리한다(6차 재리뷰 p4).
        val contendedCount = filterResult.expirableIds.size - expiredCount
        return BookingExpiryResult(
            expiredCount = expiredCount,
            skippedCount = candidateIds.size - filterResult.expirableIds.size,
            skippedSettledCount = filterResult.skippedSettledCount,
            contendedCount = contendedCount,
        )
    }
}
