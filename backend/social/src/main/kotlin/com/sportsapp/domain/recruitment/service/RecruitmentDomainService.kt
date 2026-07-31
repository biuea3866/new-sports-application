package com.sportsapp.domain.recruitment.service

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.payment.OrderPaymentLiveness
import com.sportsapp.domain.recruitment.RecruitmentFeatureFlagKeys
import com.sportsapp.domain.recruitment.dto.ApplicationDetail
import com.sportsapp.domain.recruitment.dto.ApplicationExpiryCandidate
import com.sportsapp.domain.recruitment.dto.ApplicationExpiryFilterResult
import com.sportsapp.domain.recruitment.dto.ApplicationExpiryTtlPolicy
import com.sportsapp.domain.recruitment.dto.ApplicationWithRecruitmentTitle
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.exception.RecruitmentBusyException
import com.sportsapp.domain.recruitment.exception.RecruitmentFullException
import com.sportsapp.domain.recruitment.policy.CancellationPolicy
import com.sportsapp.domain.recruitment.repository.ApplicationCustomRepository
import com.sportsapp.domain.recruitment.repository.ApplicationRepository
import com.sportsapp.domain.recruitment.repository.RecruitmentCustomRepository
import com.sportsapp.domain.recruitment.repository.RecruitmentRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.ZonedDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

private val LOCK_TTL = Duration.ofSeconds(10)
private val LOCK_WAIT_TIMEOUT = Duration.ofSeconds(5)
private val LOCK_RETRY_INTERVAL = Duration.ofMillis(50)
private const val REFUND_SCALE = 2

@Service
class RecruitmentDomainService(
    private val recruitmentRepository: RecruitmentRepository,
    private val applicationRepository: ApplicationRepository,
    private val distributedLock: DistributedLock,
    private val cancellationPolicy: CancellationPolicy,
    private val domainEventPublisher: DomainEventPublisher,
    private val recruitmentCustomRepository: RecruitmentCustomRepository,
    private val applicationCustomRepository: ApplicationCustomRepository,
    private val featureFlagEvaluator: FeatureFlagEvaluator,
) {

    fun create(
        title: String,
        description: String?,
        capacity: Int,
        feeAmount: BigDecimal,
        activityAt: ZonedDateTime,
        applicationDeadline: ZonedDateTime,
        communityId: Long?,
        recruiterUserId: Long,
    ): Recruitment {
        val recruitment = Recruitment.create(
            title = title,
            description = description,
            capacity = capacity,
            feeAmount = feeAmount,
            activityAt = activityAt,
            applicationDeadline = applicationDeadline,
            communityId = communityId,
            recruiterUserId = recruiterUserId,
        )
        return recruitmentRepository.save(recruitment)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun apply(recruitmentId: Long, applicantUserId: Long): Long {
        val lockKey = "recruitment:$recruitmentId"
        val lockValue = "user:$applicantUserId"
        if (!spinLock(lockKey, lockValue)) throw RecruitmentBusyException(recruitmentId)
        registerUnlockOnCompletion(lockKey, lockValue)
        return doApply(recruitmentId, applicantUserId, lockKey, lockValue).id
    }

    private fun doApply(recruitmentId: Long, applicantUserId: Long, lockKey: String, lockValue: String): Application {
        try {
            val recruitment = recruitmentRepository.findForUpdateById(recruitmentId)
                ?: throw ResourceNotFoundException("Recruitment", recruitmentId)
            val activeCount = applicationRepository.countActiveByRecruitmentId(recruitmentId)
            recruitment.requireApplicable(activeCount)
            val saved = applicationRepository.save(Application.create(recruitmentId, applicantUserId))
            recruitment.closeWhenFull(activeCount + 1)
            recruitmentRepository.save(recruitment)
            return saved
        } finally {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                distributedLock.unlock(lockKey, lockValue)
            }
        }
    }

    private fun registerUnlockOnCompletion(lockKey: String, lockValue: String) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                distributedLock.unlock(lockKey, lockValue)
            }
        })
    }

    private fun spinLock(key: String, value: String): Boolean {
        val deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT.toMillis()
        while (System.currentTimeMillis() < deadline) {
            if (distributedLock.tryLock(key, value, LOCK_TTL)) return true
            Thread.sleep(LOCK_RETRY_INTERVAL.toMillis())
        }
        return false
    }

    /**
     * 참가비>0 신청의 PG 확정(paymentId 지정) 또는 참가비==0 신청의 무결제 즉시확정(paymentId=null)을 처리한다.
     * PG 개시 자체는 BE-55(W3)에서 OrderType.RECRUITMENT 배선 후 연결된다 — 본 메서드는 결제 성사 여부만 반영한다.
     */
    fun confirmApplication(applicationId: Long, paymentId: Long?): Application {
        val application = applicationRepository.findById(applicationId)
            ?: throw ResourceNotFoundException("Application", applicationId)
        if (paymentId != null) application.confirm(paymentId) else application.confirmFree()
        return applicationRepository.save(application)
    }

    fun cancelPendingApplication(applicationId: Long): Application {
        val application = applicationRepository.findById(applicationId)
            ?: throw ResourceNotFoundException("Application", applicationId)
        application.cancelPending()
        val saved = applicationRepository.save(application)
        reopenRecruitmentIfBelowCapacity(saved.recruitmentId)
        return saved
    }

    fun cancelApplication(applicationId: Long, applicantUserId: Long): Application {
        val application = applicationRepository.findById(applicationId)
            ?: throw ResourceNotFoundException("Application", applicationId)
        application.requireOwnedBy(applicantUserId)
        val recruitment = recruitmentRepository.findById(application.recruitmentId)
            ?: throw ResourceNotFoundException("Recruitment", application.recruitmentId)
        application.cancelByApplicant(recruitment.applicationDeadline, refundAmountFor(recruitment))
        val saved = applicationRepository.save(application)
        domainEventPublisher.publishAll(saved.pullDomainEvents())
        return saved
    }

    private fun refundAmountFor(recruitment: Recruitment): BigDecimal {
        val feeRate = cancellationPolicy.feeRateFor(recruitment.applicationDeadline)
        return recruitment.feeAmount.multiply(BigDecimal.ONE.subtract(feeRate)).setScale(REFUND_SCALE, RoundingMode.HALF_UP)
    }

    fun cancelRecruitment(recruitmentId: Long, recruiterUserId: Long): Recruitment {
        val recruitment = recruitmentRepository.findById(recruitmentId)
            ?: throw ResourceNotFoundException("Recruitment", recruitmentId)
        recruitment.cancelByHost(recruiterUserId)
        val saved = recruitmentRepository.save(recruitment)
        refundConfirmedApplications(saved)
        return saved
    }

    private fun refundConfirmedApplications(recruitment: Recruitment) {
        val events = applicationRepository.findConfirmedByRecruitmentId(recruitment.id).flatMap { application ->
            application.cancelForRecruitmentCancellation(recruitment.feeAmount)
            applicationRepository.save(application)
            application.pullDomainEvents()
        }
        domainEventPublisher.publishAll(events)
    }

    fun getRecruitment(recruitmentId: Long): Recruitment =
        recruitmentRepository.findById(recruitmentId)
            ?: throw ResourceNotFoundException("Recruitment", recruitmentId)

    fun getApplicationById(applicationId: Long): Application =
        applicationRepository.findById(applicationId)
            ?: throw ResourceNotFoundException("Application", applicationId)

    // 주문상세(order-detail) 단건 조회용 — 본인 소유 검증 + 모집명·참가비 조인.
    fun getApplicationDetailBy(applicationId: Long, requesterUserId: Long): ApplicationDetail {
        val application = applicationRepository.findById(applicationId)
            ?: throw ResourceNotFoundException("Application", applicationId)
        application.requireOwnedBy(requesterUserId)
        val recruitment = recruitmentRepository.findById(application.recruitmentId)
            ?: throw ResourceNotFoundException("Recruitment", application.recruitmentId)
        return ApplicationDetail(
            applicationId = application.id,
            recruitmentId = recruitment.id,
            recruitmentTitle = recruitment.title,
            status = application.status,
            feeAmount = recruitment.feeAmount,
            paymentId = application.paymentId,
            createdAt = application.createdAt,
        )
    }

    fun listRecruitments(communityId: Long?): List<Recruitment> =
        recruitmentRepository.findAll(communityId)

    fun findApplications(recruitmentId: Long, requesterUserId: Long): List<Application> {
        val recruitment = recruitmentRepository.findById(recruitmentId)
            ?: throw ResourceNotFoundException("Recruitment", recruitmentId)
        recruitment.requireRecruiter(requesterUserId)
        return applicationRepository.findByRecruitmentId(recruitmentId)
    }

    fun findApplicationsBy(applicantUserId: Long): List<Application> =
        applicationRepository.findByApplicantUserId(applicantUserId)

    // catalog 통합검색용 — status=OPEN 고정 + keyword 부분 일치. CLOSED/CANCELLED는 결과에서 제외한다.
    fun searchOpenRecruitments(keyword: String?, pageable: Pageable): Page<Recruitment> =
        recruitmentCustomRepository.searchOpen(keyword, pageable)

    // order 통합조회용 — Application에 모집명(title)을 조인한 표시용 프로젝션.
    fun listApplicationsWithTitleBy(applicantUserId: Long): List<ApplicationWithRecruitmentTitle> =
        applicationCustomRepository.findBy(applicantUserId)

    /**
     * W1-11d 만료 스위퍼 — PENDING이며 createdAt < (now - ttlMinutes, 빠른 TTL)이고
     * id > afterId(청크 커서)인 신청 후보를 최대 limit건 조회한다. 시간 계산은 이 메서드
     * 내부에서 해결한다(no-time-parameter — 캡슐화 메서드에 시간을 인자로 넘기지 않는다).
     * `facility-booking`(W1-11c) `findExpirableBookingCandidates`와 동일한 이유로 named
     * argument를 강제한다 — `ttlMinutes`(Long)와 `afterId`(Long)가 인접한 동일 타입이라
     * 위치 인자로 바꿔 넘기면 컴파일은 통과하되 TTL↔커서가 뒤바뀌는 오동작이 조용히
     * 재발할 수 있다.
     */
    fun findExpirableApplicationCandidates(ttlMinutes: Long, afterId: Long, limit: Int): List<ApplicationExpiryCandidate> {
        val threshold = ZonedDateTime.now().minusMinutes(ttlMinutes)
        return applicationRepository.findPendingCreatedBefore(threshold, afterId, limit)
    }

    /**
     * W1-11d 만료 스위퍼 — 만료 후보 중 실제로 취소시킬 대상을 최종 판정한다. payment로부터
     * 받은 orderId별 판정([OrderPaymentLiveness] — domain.common 공유 커널)만으로 판단하므로
     * 도메인 교차가 아니다 — 크로스 컨텍스트 조합 자체(payment 조회 → 값 변환)는
     * application(ExpirePendingApplicationsUseCase)이 수행하고, 이 메서드는 recruitment
     * 자신의 정책(두 TTL)만 적용한다. 판정 자체는 `facility-booking`(W1-11c)이 하드닝한
     * [OrderPaymentLiveness.allowsExpiry]에 위임한다 — settled 우선 판정·단조성 불변식(느린
     * TTL·빠른 TTL 두 창 모두 검사)을 이 메서드가 재구현하지 않는다.
     */
    fun filterExpirable(
        candidates: List<ApplicationExpiryCandidate>,
        liveness: Map<Long, OrderPaymentLiveness>,
        ttlPolicy: ApplicationExpiryTtlPolicy,
    ): ApplicationExpiryFilterResult {
        val now = ZonedDateTime.now()
        val fastThreshold = now.minusMinutes(ttlPolicy.ttlMinutes)
        val readyThreshold = now.minusMinutes(ttlPolicy.readyTtlMinutes)
        val settled = candidates.filter { liveness[it.applicationId] is OrderPaymentLiveness.Settled }
        val expirableIds = candidates
            .filterNot { liveness[it.applicationId] is OrderPaymentLiveness.Settled }
            .filter { candidate ->
                val candidateLiveness = liveness[candidate.applicationId] ?: OrderPaymentLiveness.None
                candidateLiveness.allowsExpiry(candidate.createdAt, readyThreshold, fastThreshold)
            }
            .map { it.applicationId }
        return ApplicationExpiryFilterResult(expirableIds = expirableIds, skippedSettledCount = settled.size)
    }

    /**
     * W1-11d 만료 스위퍼 — 청크 단위로 PENDING → CANCELLED CAS 전이한다
     * ([ApplicationRepository.tryExpire]). 트랜잭션 경계는 이 메서드를 호출하는 UseCase
     * (`ExpireApplicationChunkUseCase`)가 소유한다 — DomainService는 트랜잭션을 선언하지
     * 않는다. CAS 성공 시에만 [reopenRecruitmentIfBelowCapacity]로 정원 복원을 시도한다 —
     * 만료가 실제로 발생하지 않았는데(CAS 경합 패배) 정원을 재계산할 필요가 없다.
     */
    fun expireApplications(applicationIds: List<Long>): Int {
        if (applicationIds.isEmpty()) return 0
        return applicationIds.count { applicationId ->
            val expired = applicationRepository.tryExpire(applicationId)
            if (expired) {
                applicationRepository.findById(applicationId)?.let { reopenRecruitmentIfBelowCapacity(it.recruitmentId) }
            }
            expired
        }
    }

    /**
     * W1-11d — 정원이 가득 차 CLOSED로 전이됐던 모집이 신청 취소(만료 스위퍼·결제 취소 웹훅
     * 공통)로 정원에 여유가 생기면 다시 신청 가능하도록 OPEN으로 되돌린다. 이미 CLOSED가
     * 아니거나(CANCELLED 포함) 여전히 정원이 가득 찼으면 [Recruitment.reopenIfBelowCapacity]가
     * 자체 가드로 아무 것도 하지 않는다.
     */
    private fun reopenRecruitmentIfBelowCapacity(recruitmentId: Long) {
        val recruitment = recruitmentRepository.findById(recruitmentId) ?: return
        val activeCount = applicationRepository.countActiveByRecruitmentId(recruitmentId)
        recruitment.reopenIfBelowCapacity(activeCount)
        recruitmentRepository.save(recruitment)
    }

    /**
     * recruitment.expiry.enabled 운영 킬 스위치 판정 — 부팅 고정 설정이 아니라 매 스케줄 주기
     * `FeatureFlagEvaluator`로 런타임 조회한다(no-conditional-on-property).
     */
    fun isExpiryEnabled(): Boolean =
        featureFlagEvaluator.isEnabled(RecruitmentFeatureFlagKeys.EXPIRY_ENABLED, FeatureContext.anonymous(), true)
}
