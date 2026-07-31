package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.config.RecruitmentApplicationExpiryProperties
import com.sportsapp.application.recruitment.dto.ApplicationExpiryResult
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.recruitment.dto.ApplicationExpiryCandidate
import com.sportsapp.domain.recruitment.dto.ApplicationExpiryTtlPolicy
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service

/**
 * W1-11d — recruitment PENDING 신청 만료 스위퍼(F-A 고립 신청 취소).
 *
 * 청크 단위(recruitmentApplicationExpiryProperties.chunkSize)로 PENDING 후보(createdAt
 * 포함)를 조회 → payment에게 orderId별 결제 생존 판정([OrderPaymentLiveness] —
 * domain.common 공유 커널)을 물어([PaymentDomainService.findPaymentLiveness]) → recruitment
 * 자신의 정책(빠른/느린 TTL)으로 최종 취소 대상을 판정
 * ([RecruitmentDomainService.filterExpirable]) → 나머지만 [ExpireApplicationChunkUseCase]가
 * 청크 단위 독립 트랜잭션으로 커밋한다. 한 주기 상한(maxChunksPerRun)만큼만 청크를 처리한다.
 *
 * 크로스 컨텍스트 조합은 이 application 레이어에서만 수행한다 — payment의
 * `PaymentLivenessQueryResult`(payment 전용 dto)는 이 UseCase까지만 오고, recruitment에는
 * `livenessByOrderId`(domain.common 공유 커널 타입의 맵)만 넘긴다.
 *
 * **청크 커서(afterId)**: 건너뛴(결제 진행 중) 신청은 다음 청크 조회에서 id > afterId 조건으로
 * 제외된다 — 커서가 없으면 같은 건이 매 청크 재조회되어 head-of-line blocking이 생긴다.
 */
@Service
class ExpirePendingApplicationsUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
    private val paymentDomainService: PaymentDomainService,
    private val expireApplicationChunkUseCase: ExpireApplicationChunkUseCase,
    private val recruitmentApplicationExpiryProperties: RecruitmentApplicationExpiryProperties,
) {
    fun execute(): ApplicationExpiryResult =
        processChunks(
            afterId = 0L,
            chunksLeft = recruitmentApplicationExpiryProperties.maxChunksPerRun,
            accumulated = ApplicationExpiryResult.empty(),
        )

    private tailrec fun processChunks(afterId: Long, chunksLeft: Int, accumulated: ApplicationExpiryResult): ApplicationExpiryResult {
        val candidates = if (chunksLeft <= 0) {
            emptyList()
        } else {
            recruitmentDomainService.findExpirableApplicationCandidates(
                ttlMinutes = recruitmentApplicationExpiryProperties.ttlMinutes,
                afterId = afterId,
                limit = recruitmentApplicationExpiryProperties.chunkSize,
            )
        }
        if (candidates.isEmpty()) return accumulated
        val chunkResult = processCandidates(candidates)
        return processChunks(afterId = candidates.last().applicationId, chunksLeft = chunksLeft - 1, accumulated = accumulated + chunkResult)
    }

    private fun processCandidates(candidates: List<ApplicationExpiryCandidate>): ApplicationExpiryResult {
        val candidateIds = candidates.map { it.applicationId }
        val liveness = paymentDomainService.findPaymentLiveness(orderType = OrderType.RECRUITMENT, orderIds = candidateIds)
        val filterResult = recruitmentDomainService.filterExpirable(
            candidates = candidates,
            liveness = liveness.livenessByOrderId,
            ttlPolicy = ApplicationExpiryTtlPolicy(
                ttlMinutes = recruitmentApplicationExpiryProperties.ttlMinutes,
                readyTtlMinutes = recruitmentApplicationExpiryProperties.readyTtlMinutes,
            ),
        )
        val expiredCount = expireApplicationChunkUseCase.execute(filterResult.expirableIds)
        // CAS(tryExpire)에서 경합에 진 건 — expirableIds로 판정됐으나 실제 전이는 실패한 건수.
        val contendedCount = filterResult.expirableIds.size - expiredCount
        return ApplicationExpiryResult(
            expiredCount = expiredCount,
            skippedCount = candidateIds.size - filterResult.expirableIds.size,
            skippedSettledCount = filterResult.skippedSettledCount,
            contendedCount = contendedCount,
        )
    }
}
