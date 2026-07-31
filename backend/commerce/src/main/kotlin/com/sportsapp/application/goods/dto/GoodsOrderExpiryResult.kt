package com.sportsapp.application.goods.dto

/**
 * W1-11a 만료 스위퍼 1회 실행(또는 청크 1건) 결과 — 만료 건수·건너뛴 건수를 담는다.
 *
 * `facility-booking`(W1-11c)의 `BookingExpiryResult`와 동일 구조다. [skippedSettledCount]를
 * [skippedCount]와 분리한 이유: settled(결제 완료)로 건너뛴 건은 웹훅 유실·컨슈머 다운으로
 * "돈은 받았는데 주문이 여전히 PENDING"인 **환불 판단이 필요한 이상 신호**다. live(결제
 * 진행 중)로 건너뛴 정상 흐름과 뭉뚱그리면 경보가 불가능하다.
 *
 * [contendedCount]: `filterExpirable`이 만료 대상으로 판정했으나
 * [com.sportsapp.domain.goods.repository.GoodsOrderCustomRepository.tryExpire] CAS(WHERE
 * status='PENDING')가 실패한 건 — 청크 처리 도중 다른 트랜잭션(webhook 확정)이 먼저
 * CONFIRMED로 전이시켜 경합에서 진 경우다. 별도 카운터로 분리해 "확정과 만료가 얼마나
 * 부딪히는가"를 관측한다.
 *
 * [chunkFailedCount](재리뷰 p2): 청크 하나가 재시도([ExpireGoodsOrderChunkUseCase]의
 * `@Retryable`)에도 불구하고 끝내 실패해 [com.sportsapp.application.goods.usecase.ExpirePendingGoodsOrdersUseCase]가
 * 격리한 후보 건수 — 이 청크의 만료 판정은 유실됐지만(다음 주기에 재평가됨) 나머지
 * 청크·주기 전체는 계속 진행됐다는 뜻이다. 0보다 크면 `Stock` 동시 쓰기 경합이 재시도
 * 예산을 넘어설 만큼 심하다는 신호다.
 */
data class GoodsOrderExpiryResult(
    val expiredCount: Int,
    val skippedCount: Int,
    val skippedSettledCount: Int = 0,
    val contendedCount: Int = 0,
    val chunkFailedCount: Int = 0,
) {
    operator fun plus(other: GoodsOrderExpiryResult): GoodsOrderExpiryResult = GoodsOrderExpiryResult(
        expiredCount = expiredCount + other.expiredCount,
        skippedCount = skippedCount + other.skippedCount,
        skippedSettledCount = skippedSettledCount + other.skippedSettledCount,
        contendedCount = contendedCount + other.contendedCount,
        chunkFailedCount = chunkFailedCount + other.chunkFailedCount,
    )

    companion object {
        fun empty(): GoodsOrderExpiryResult = GoodsOrderExpiryResult(
            expiredCount = 0,
            skippedCount = 0,
            skippedSettledCount = 0,
            contendedCount = 0,
            chunkFailedCount = 0,
        )
    }
}
