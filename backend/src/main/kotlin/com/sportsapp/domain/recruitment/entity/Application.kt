package com.sportsapp.domain.recruitment.entity

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.recruitment.event.ApplicationRefundRequestedEvent
import com.sportsapp.domain.recruitment.exception.ApplicationCancellationClosedException
import com.sportsapp.domain.recruitment.exception.InvalidApplicationStateException
import com.sportsapp.domain.recruitment.exception.UnauthorizedApplicationAccessException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.math.BigDecimal
import java.time.ZonedDateTime

@Entity
@Table(name = "applications")
class Application private constructor(
    @Column(name = "recruitment_id", nullable = false)
    val recruitmentId: Long,

    @Column(name = "applicant_user_id", nullable = false)
    val applicantUserId: Long,

    initialStatus: ApplicationStatus,
    initialPaymentId: Long?,
) : JpaAuditingBase() {

    @Transient
    private var _domainEvents: MutableList<DomainEvent>? = null

    private val domainEvents: MutableList<DomainEvent>
        get() = _domainEvents ?: mutableListOf<DomainEvent>().also { _domainEvents = it }

    fun pullDomainEvents(): List<DomainEvent> {
        val events = domainEvents.toList()
        domainEvents.clear()
        return events
    }

    private fun registerEvent(event: DomainEvent) {
        domainEvents.add(event)
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ApplicationStatus = initialStatus
        private set

    @Column(name = "payment_id", nullable = true)
    var paymentId: Long? = initialPaymentId
        private set

    fun confirm(paymentId: Long) {
        if (status == ApplicationStatus.CONFIRMED) {
            return
        }
        if (!status.canTransitTo(ApplicationStatus.CONFIRMED)) {
            throw InvalidApplicationStateException(status, ApplicationStatus.CONFIRMED)
        }
        status = ApplicationStatus.CONFIRMED
        this.paymentId = paymentId
    }

    /**
     * 참가비 0원(무료) 모집의 신청을 결제 없이 즉시 확정한다. paymentId는 채워지지 않는다.
     */
    fun confirmFree() {
        if (status == ApplicationStatus.CONFIRMED) {
            return
        }
        if (!status.canTransitTo(ApplicationStatus.CONFIRMED)) {
            throw InvalidApplicationStateException(status, ApplicationStatus.CONFIRMED)
        }
        status = ApplicationStatus.CONFIRMED
    }

    fun requireOwnedBy(userId: Long) {
        if (applicantUserId != userId) {
            throw UnauthorizedApplicationAccessException(id)
        }
    }

    fun cancelPending() {
        if (status == ApplicationStatus.CANCELLED) {
            return
        }
        if (!status.canTransitTo(ApplicationStatus.CANCELLED)) {
            throw InvalidApplicationStateException(status, ApplicationStatus.CANCELLED)
        }
        status = ApplicationStatus.CANCELLED
    }

    fun cancelByApplicant(applicationDeadline: ZonedDateTime, refundAmount: BigDecimal) {
        if (status == ApplicationStatus.CANCELLED || status == ApplicationStatus.REFUNDED) {
            return
        }
        if (ZonedDateTime.now().isAfter(applicationDeadline)) {
            throw ApplicationCancellationClosedException(id)
        }
        if (!status.canTransitTo(ApplicationStatus.CANCELLED)) {
            throw InvalidApplicationStateException(status, ApplicationStatus.CANCELLED)
        }
        status = ApplicationStatus.CANCELLED
        registerEvent(
            ApplicationRefundRequestedEvent(
                applicationId = id,
                paymentId = paymentId,
                refundAmount = refundAmount,
                reason = "APPLICANT_CANCEL",
            ),
        )
    }

    /**
     * 개설자 모집 취소로 인한 전액환불 대상 전이. 신청 마감 경과 여부와 무관하게(호스트가
     * 모집 자체를 취소했으므로 마감 개념이 무의미) 전액을 환불 요청 이벤트에 담는다.
     * `cancelByApplicant`와 달리 신청자 귀책(APPLICANT_CANCEL)이 아니므로 별도 reason을 사용한다.
     */
    fun cancelForRecruitmentCancellation(refundAmount: BigDecimal) {
        if (status == ApplicationStatus.CANCELLED || status == ApplicationStatus.REFUNDED) {
            return
        }
        if (!status.canTransitTo(ApplicationStatus.CANCELLED)) {
            throw InvalidApplicationStateException(status, ApplicationStatus.CANCELLED)
        }
        status = ApplicationStatus.CANCELLED
        registerEvent(
            ApplicationRefundRequestedEvent(
                applicationId = id,
                paymentId = paymentId,
                refundAmount = refundAmount,
                reason = "RECRUITMENT_CANCELLED",
            ),
        )
    }

    fun markRefunded() {
        if (status == ApplicationStatus.REFUNDED) {
            return
        }
        if (!status.canTransitTo(ApplicationStatus.REFUNDED)) {
            throw InvalidApplicationStateException(status, ApplicationStatus.REFUNDED)
        }
        status = ApplicationStatus.REFUNDED
    }

    companion object {
        fun create(recruitmentId: Long, applicantUserId: Long): Application = Application(
            recruitmentId = recruitmentId,
            applicantUserId = applicantUserId,
            initialStatus = ApplicationStatus.PENDING,
            initialPaymentId = null,
        )

        fun reconstitute(
            recruitmentId: Long,
            applicantUserId: Long,
            status: ApplicationStatus,
            paymentId: Long?,
        ): Application = Application(
            recruitmentId = recruitmentId,
            applicantUserId = applicantUserId,
            initialStatus = status,
            initialPaymentId = paymentId,
        )
    }
}
