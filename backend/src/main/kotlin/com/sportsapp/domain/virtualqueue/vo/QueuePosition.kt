package com.sportsapp.domain.virtualqueue.vo

import kotlin.math.ceil

/**
 * 순번·ETA 계산 VO.
 *
 * admission 판정([admitted])은 고정 시퀀스(`seq <= admittedCount`)로 하고, 표시용 앞선 인원
 * ([aheadCount])은 `ZRANK` 기반 rank를 그대로 노출한다 — 판정과 표시의 입력을 분리한다.
 *
 * `ZRANK`는 admission·이탈로 살아있는 멤버가 제거될 때마다 즉시 변한다. 판정을 rank로 하면,
 * 앞선 사용자가 admission으로 제거되는 순간 뒤 사용자의 rank가 같은 틱 안에서 문턱 아래로
 * 붕괴해 연쇄 과다 admission이 발생한다(redis-contract §0-1). 고정 시퀀스는 ZADD 이후 다른
 * 멤버의 제거·추가에 영향받지 않아 이 문제가 없다.
 */
data class QueuePosition private constructor(
    val aheadCount: Long,
    val admitted: Boolean,
    val etaSeconds: Long,
) {

    companion object {

        /**
         * @param rank ZRANK — 표시용 앞선 인원(동적, 이탈 시 자연 전진)
         * @param seq ZSCORE — admission 판정용 고정 시퀀스(제거 영향 없음)
         * @param admittedCount 현재 클러스터 admission 고수위
         * @param batchSize 틱당 admission 전진 배치 크기
         * @param tickSeconds admission pump 주기(초)
         */
        fun of(
            rank: Int,
            seq: Long,
            admittedCount: Long,
            batchSize: Int,
            tickSeconds: Int,
        ): QueuePosition {
            require(batchSize > 0) { "batchSize must be positive: $batchSize" }
            require(tickSeconds >= 0) { "tickSeconds must not be negative: $tickSeconds" }

            val admitted = seq <= admittedCount
            val etaSeconds = if (admitted) 0L else calculateEtaSeconds(seq, admittedCount, batchSize, tickSeconds)
            return QueuePosition(aheadCount = rank.toLong(), admitted = admitted, etaSeconds = etaSeconds)
        }

        private fun calculateEtaSeconds(
            seq: Long,
            admittedCount: Long,
            batchSize: Int,
            tickSeconds: Int,
        ): Long {
            val remainingTicksNeeded = ceil((seq - admittedCount).toDouble() / batchSize).toLong()
            return remainingTicksNeeded * tickSeconds
        }
    }
}
