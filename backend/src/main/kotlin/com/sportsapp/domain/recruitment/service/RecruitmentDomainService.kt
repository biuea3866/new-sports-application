package com.sportsapp.domain.recruitment.service

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.recruitment.dto.ApplicationDetail
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
        return applicationRepository.save(application)
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
}
