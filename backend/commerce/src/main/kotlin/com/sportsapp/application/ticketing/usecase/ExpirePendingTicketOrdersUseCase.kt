package com.sportsapp.application.ticketing.usecase

import com.sportsapp.application.ticketing.config.TicketOrderExpiryProperties
import com.sportsapp.application.ticketing.dto.TicketOrderExpiryResult
import com.sportsapp.domain.ticketing.dto.TicketOrderExpiryCandidate
import com.sportsapp.domain.ticketing.dto.TicketOrderExpiryTtlPolicy
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.service.PaymentDomainService
import org.springframework.stereotype.Service

/**
 * W1-11b — ticketing PENDING 주문 만료 스위퍼. booking(W1-11c)의
 * [com.sportsapp.application.booking.usecase.ExpirePendingBookingsUseCase]와 동일한 구조 —
 * 청크 단위(ticketOrderExpiryProperties.chunkSize)로 PENDING 후보(createdAt 포함)를 조회 →
 * payment에게 orderId별 결제 생존 판정([com.sportsapp.domain.common.payment.OrderPaymentLiveness] —
 * domain.common 공유 커널)을 물어([PaymentDomainService.findPaymentLiveness]) → ticketing 자신의
 * 정책(빠른/느린 TTL)으로 최종 만료 대상을 판정([TicketingDomainService.filterExpirableTicketOrders]) →
 * 나머지만 [ExpireTicketOrderChunkUseCase]가 청크 단위 독립 트랜잭션으로 커밋한다. 한 주기 상한
 * (maxChunksPerRun)만큼만 청크를 처리한다.
 *
 * 크로스 컨텍스트 조합(payment의 `PaymentLivenessQueryResult` → `livenessByOrderId` 맵 변환)은
 * 이 application 레이어에서만 수행한다 — ticketing에는 domain.common 공유 커널 타입의 맵만
 * 넘긴다.
 *
 * **청크 커서(afterId)**: 건너뛴(결제 진행 중) 주문은 다음 청크 조회에서 id > afterId 조건으로
 * 제외된다 — 커서가 없으면 같은 건이 매 청크 재조회돼 head-of-line blocking이 생긴다. 커서는
 * 이번 실행(execute 1회) 안에서만 유효하고, 다음 스케줄 주기는 afterId=0부터 다시 전체
 * 후보를 훑는다.
 */
@Service
class ExpirePendingTicketOrdersUseCase(
    private val ticketingDomainService: TicketingDomainService,
    private val paymentDomainService: PaymentDomainService,
    private val expireTicketOrderChunkUseCase: ExpireTicketOrderChunkUseCase,
    private val ticketOrderExpiryProperties: TicketOrderExpiryProperties,
) {
    fun execute(): TicketOrderExpiryResult =
        processChunks(afterId = 0L, chunksLeft = ticketOrderExpiryProperties.maxChunksPerRun, accumulated = TicketOrderExpiryResult.empty())

    // guard clause 다수 사용의 부산물(private-be-code-convention 권장) — tailrec 종료 조건
    // 2개(chunksLeft 소진·candidates 없음)의 조기 반환 + 재귀 호출 자체가 3번째 return으로
    // 잡힌다. tailrec 최적화를 유지하려면 이 형태가 필수라 병합하지 않는다.
    @Suppress("ReturnCount")
    private tailrec fun processChunks(afterId: Long, chunksLeft: Int, accumulated: TicketOrderExpiryResult): TicketOrderExpiryResult {
        if (chunksLeft <= 0) return accumulated
        val candidates = ticketingDomainService.findExpirableTicketOrderCandidates(
            ttlMinutes = ticketOrderExpiryProperties.ttlMinutes,
            afterId = afterId,
            limit = ticketOrderExpiryProperties.chunkSize,
        )
        if (candidates.isEmpty()) return accumulated
        val chunkResult = processCandidates(candidates)
        return processChunks(afterId = candidates.last().orderId, chunksLeft = chunksLeft - 1, accumulated = accumulated + chunkResult)
    }

    private fun processCandidates(candidates: List<TicketOrderExpiryCandidate>): TicketOrderExpiryResult {
        val candidateIds = candidates.map { it.orderId }
        val liveness = paymentDomainService.findPaymentLiveness(orderType = OrderType.TICKETING, orderIds = candidateIds)
        val filterResult = ticketingDomainService.filterExpirableTicketOrders(
            candidates = candidates,
            liveness = liveness.livenessByOrderId,
            ttlPolicy = TicketOrderExpiryTtlPolicy(
                ttlMinutes = ticketOrderExpiryProperties.ttlMinutes,
                readyTtlMinutes = ticketOrderExpiryProperties.readyTtlMinutes,
            ),
        )
        val expiredCount = expireTicketOrderChunkUseCase.execute(filterResult.expirableIds)
        // CAS(tryExpire)에서 경합에 진 건 — expirableIds로 판정됐으나 실제 전이는 실패한 건수.
        val contendedCount = filterResult.expirableIds.size - expiredCount
        return TicketOrderExpiryResult(
            expiredCount = expiredCount,
            skippedCount = candidateIds.size - filterResult.expirableIds.size,
            skippedSettledCount = filterResult.skippedSettledCount,
            contendedCount = contendedCount,
        )
    }
}
