package com.sportsapp.domain.recruitment.entity

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.recruitment.event.ApplicationRefundRequestedEvent
import com.sportsapp.domain.recruitment.exception.ApplicationCancellationClosedException
import com.sportsapp.domain.recruitment.exception.InvalidApplicationStateException
import com.sportsapp.domain.recruitment.vo.ApplicationStatus
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
