package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.config.GoodsOrderExpiryProperties
import com.sportsapp.application.goods.dto.GoodsOrderExpiryResult
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.goods.dto.GoodsOrderExpiryCandidate
import com.sportsapp.domain.goods.dto.GoodsOrderExpiryTtlPolicy
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.payment.service.PaymentDomainService
import org.springframework.stereotype.Service

/**
 * W1-11a — goods PENDING 주문 만료 스위퍼(F-A 고립 주문 만료).
 *
 * `facility-booking`(W1-11c)의 `ExpirePendingBookingsUseCase`가 정본이다 — 청크 단위
 * (goodsOrderExpiryProperties.chunkSize)로 PENDING 후보(createdAt 포함)를 조회 → payment에게
 * orderId별 결제 생존 판정([com.sportsapp.domain.common.payment.OrderPaymentLiveness] —
 * domain.common 공유 커널)을 물어([PaymentDomainService.findPaymentLiveness]) → goods
 * 자신의 정책(빠른/느린 TTL)으로 최종 만료 대상을 판정([GoodsDomainService.filterExpirable])
 * → 나머지만 [ExpireGoodsOrderChunkUseCase]가 청크 단위 독립 트랜잭션으로 커밋한다. 한 주기
 * 상한(maxChunksPerRun)만큼만 청크를 처리한다.
 *
 * **payment는 시간 창(TTL 분값)을 갖지 않는다** — payment 상태(settled/live/attempting/none)
 * 만 받고, TTL 분값은 이 UseCase가 [GoodsOrderExpiryProperties]에서 읽어
 * [GoodsOrderExpiryTtlPolicy]로 묶어 goods DomainService에 넘긴다. 크로스 컨텍스트 조합은
 * 이 application 레이어에서만 수행한다 — payment의 `PaymentLivenessQueryResult`(payment
 * 전용 dto)는 이 UseCase까지만 오고, goods에는 `livenessByOrderId`(domain.common 공유
 * 커널 타입의 맵)만 넘긴다.
 *
 * **명명 인자 강제**: [GoodsDomainService.findExpirableGoodsOrderCandidates]의
 * `ttlMinutes`(Long)와 `afterId`(Long)는 인접한 동일 타입이라 위치 인자로 바꿔 넘겨도
 * 컴파일이 통과해 TTL↔커서가 뒤바뀌는 오동작이 조용히 재발할 수 있어 named argument로
 * 호출한다.
 *
 * **청크 커서(afterId)**: 건너뛴(결제 진행 중) 주문은 다음 청크 조회에서 id > afterId
 * 조건으로 제외된다 — 커서가 없으면 같은 건이 매 청크 재조회되어 head-of-line blocking·
 * skippedCount 중복 집계가 생긴다.
 */
@Service
class ExpirePendingGoodsOrdersUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val paymentDomainService: PaymentDomainService,
    private val expireGoodsOrderChunkUseCase: ExpireGoodsOrderChunkUseCase,
    private val goodsOrderExpiryProperties: GoodsOrderExpiryProperties,
) {
    fun execute(): GoodsOrderExpiryResult =
        processChunks(afterId = 0L, chunksLeft = goodsOrderExpiryProperties.maxChunksPerRun, accumulated = GoodsOrderExpiryResult.empty())

    private tailrec fun processChunks(afterId: Long, chunksLeft: Int, accumulated: GoodsOrderExpiryResult): GoodsOrderExpiryResult {
        if (chunksLeft <= 0) return accumulated
        val candidates = goodsDomainService.findExpirableGoodsOrderCandidates(
            ttlMinutes = goodsOrderExpiryProperties.ttlMinutes,
            afterId = afterId,
            limit = goodsOrderExpiryProperties.chunkSize,
        )
        if (candidates.isEmpty()) return accumulated
        val chunkResult = processCandidates(candidates)
        return processChunks(afterId = candidates.last().orderId, chunksLeft = chunksLeft - 1, accumulated = accumulated + chunkResult)
    }

    private fun processCandidates(candidates: List<GoodsOrderExpiryCandidate>): GoodsOrderExpiryResult {
        val candidateIds = candidates.map { it.orderId }
        val liveness = paymentDomainService.findPaymentLiveness(orderType = OrderType.GOODS, orderIds = candidateIds)
        val filterResult = goodsDomainService.filterExpirable(
            candidates = candidates,
            liveness = liveness.livenessByOrderId,
            ttlPolicy = GoodsOrderExpiryTtlPolicy(
                ttlMinutes = goodsOrderExpiryProperties.ttlMinutes,
                readyTtlMinutes = goodsOrderExpiryProperties.readyTtlMinutes,
            ),
        )
        val expiredCount = expireGoodsOrderChunkUseCase.execute(filterResult.expirableIds)
        // CAS(tryExpire)에서 경합에 진 건 — expirableIds로 판정됐으나 실제 전이는 실패한 건수.
        val contendedCount = filterResult.expirableIds.size - expiredCount
        return GoodsOrderExpiryResult(
            expiredCount = expiredCount,
            skippedCount = candidateIds.size - filterResult.expirableIds.size,
            skippedSettledCount = filterResult.skippedSettledCount,
            contendedCount = contendedCount,
        )
    }
}
