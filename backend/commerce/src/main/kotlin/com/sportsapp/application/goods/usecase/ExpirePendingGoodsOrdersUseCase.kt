package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.config.GoodsOrderExpiryProperties
import com.sportsapp.application.goods.dto.GoodsOrderExpiryResult
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.goods.dto.GoodsOrderExpiryCandidate
import com.sportsapp.domain.goods.dto.GoodsOrderExpiryTtlPolicy
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.payment.service.PaymentDomainService
import org.slf4j.LoggerFactory
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
 *
 * **청크 실패 격리(재리뷰 p2)**: booking 정본(`expireBookings`)은 순수 CAS(부수 쓰기
 * 0건)라 청크 실패가 사실상 없지만, goods는 만료 시 `Stock`(`@Version`) 재고 복원 쓰기가
 * 있어 동시 구매와 경합하면 [org.springframework.orm.ObjectOptimisticLockingFailureException]이
 * 날 수 있다([ExpireGoodsOrderChunkUseCase]가 먼저 재시도하지만 그래도 실패할 수 있다).
 * 이 예외가 [processChunks] 밖(스케줄러)까지 그대로 올라가면 남은 청크가 전부 미처리되고
 * 4개 카운터가 전부 유실되므로, [processCandidates] 호출을 청크 단위로 격리해 실패를
 * [GoodsOrderExpiryResult.chunkFailedCandidateCount]로 집계하고 다음 청크로 진행한다.
 */
@Service
class ExpirePendingGoodsOrdersUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val paymentDomainService: PaymentDomainService,
    private val expireGoodsOrderChunkUseCase: ExpireGoodsOrderChunkUseCase,
    private val goodsOrderExpiryProperties: GoodsOrderExpiryProperties,
) {
    private val log = LoggerFactory.getLogger(ExpirePendingGoodsOrdersUseCase::class.java)

    fun execute(): GoodsOrderExpiryResult =
        processChunks(afterId = 0L, chunksLeft = goodsOrderExpiryProperties.maxChunksPerRun, accumulated = GoodsOrderExpiryResult.empty())

    // guard clause 다수 사용의 부산물(private-be-code-convention 권장) — tailrec 종료 조건
    // 2개(chunksLeft 소진·candidates 없음)의 조기 반환 + 재귀 호출 자체가 3번째 return으로
    // 잡힌다. tailrec 최적화를 유지하려면 이 형태가 필수라 병합하지 않는다.
    @Suppress("ReturnCount")
    private tailrec fun processChunks(afterId: Long, chunksLeft: Int, accumulated: GoodsOrderExpiryResult): GoodsOrderExpiryResult {
        if (chunksLeft <= 0) return accumulated
        val candidates = goodsDomainService.findExpirableGoodsOrderCandidates(
            ttlMinutes = goodsOrderExpiryProperties.ttlMinutes,
            afterId = afterId,
            limit = goodsOrderExpiryProperties.chunkSize,
        )
        if (candidates.isEmpty()) return accumulated
        val chunkResult = processCandidatesSafely(candidates)
        return processChunks(afterId = candidates.last().orderId, chunksLeft = chunksLeft - 1, accumulated = accumulated + chunkResult)
    }

    /**
     * [processCandidates] 실행 중 발생한 예외를 이 청크 하나로 격리한다 — 재시도
     * ([ExpireGoodsOrderChunkUseCase]의 `@Retryable`) 후에도 남은 실패(CAS 경합 소진,
     * payment 조회 오류 등)가 주기 전체(4개 카운터 유실 + 남은 청크 미처리)를 죽이지
     * 않게 한다. 커서 전진은 호출부([processChunks])가 이미 처리하므로 여기서는 실패
     * 카운트만 반환한다.
     *
     * **격리 범위는 `Exception`만**(재리뷰 p3) — `Throwable` 전체를 삼키면
     * `OutOfMemoryError` 같은 치명적 오류도 로그 한 줄로 묻히고 스케줄러가 남은
     * `maxChunksPerRun`개 청크를 계속 돈다. `InterruptedException`은 별도로 잡아
     * 인터럽트 플래그를 복원한 뒤 재throw한다.
     */
    private fun processCandidatesSafely(candidates: List<GoodsOrderExpiryCandidate>): GoodsOrderExpiryResult =
        try {
            processCandidates(candidates)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw exception
        } catch (exception: Exception) {
            log.error(
                "event=goods-order-expiry-chunk-failed candidateCount={} firstOrderId={} lastOrderId={}",
                candidates.size,
                candidates.first().orderId,
                candidates.last().orderId,
                exception,
            )
            GoodsOrderExpiryResult(
                expiredCount = 0,
                skippedCount = 0,
                skippedSettledCount = 0,
                contendedCount = 0,
                chunkFailedCandidateCount = candidates.size,
            )
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
