package com.sportsapp.domain.recruitment.entity

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.recruitment.exception.InvalidRecruitmentException
import com.sportsapp.domain.recruitment.exception.InvalidRecruitmentStateException
import com.sportsapp.domain.recruitment.exception.NotRecruiterException
import com.sportsapp.domain.recruitment.vo.RecruitmentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.ZonedDateTime

@Entity
@Table(name = "recruitments")
class Recruitment private constructor(
    @Column(name = "capacity", nullable = false)
    val capacity: Int,

    @Column(name = "fee_amount", nullable = false)
    val feeAmount: BigDecimal,

    @Column(name = "activity_at", nullable = false)
    val activityAt: ZonedDateTime,

    @Column(name = "application_deadline", nullable = false)
    val applicationDeadline: ZonedDateTime,

    @Column(name = "community_id", nullable = true)
    val communityId: Long?,

    @Column(name = "recruiter_user_id", nullable = false)
    val recruiterUserId: Long,

    initialStatus: RecruitmentStatus,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: RecruitmentStatus = initialStatus
        private set

    fun canApply(currentApplicantCount: Int): Boolean =
        status == RecruitmentStatus.OPEN && !isDeadlinePassed() && currentApplicantCount < capacity

    fun closeWhenFull(currentApplicantCount: Int) {
        if (status == RecruitmentStatus.OPEN && currentApplicantCount >= capacity) {
            status = RecruitmentStatus.CLOSED
        }
    }

    fun cancelByHost(userId: Long) {
        requireRecruiter(userId)
        if (status == RecruitmentStatus.CANCELLED) {
            return
        }
        if (!status.canTransitTo(RecruitmentStatus.CANCELLED)) {
            throw InvalidRecruitmentStateException(status, RecruitmentStatus.CANCELLED)
        }
        status = RecruitmentStatus.CANCELLED
    }

    fun requireRecruiter(userId: Long) {
        if (recruiterUserId != userId) {
            throw NotRecruiterException(id)
        }
    }

    private fun isDeadlinePassed(): Boolean = ZonedDateTime.now().isAfter(applicationDeadline)

    companion object {
        fun create(
            capacity: Int,
            feeAmount: BigDecimal,
            activityAt: ZonedDateTime,
            applicationDeadline: ZonedDateTime,
            communityId: Long?,
            recruiterUserId: Long,
        ): Recruitment {
            if (capacity <= 0) {
                throw InvalidRecruitmentException("capacity must be positive, got: $capacity")
            }
            if (feeAmount < BigDecimal.ZERO) {
                throw InvalidRecruitmentException("feeAmount must not be negative, got: $feeAmount")
            }
            return Recruitment(
                capacity = capacity,
                feeAmount = feeAmount,
                activityAt = activityAt,
                applicationDeadline = applicationDeadline,
                communityId = communityId,
                recruiterUserId = recruiterUserId,
                initialStatus = RecruitmentStatus.OPEN,
            )
        }

        fun reconstitute(
            capacity: Int,
            feeAmount: BigDecimal,
            activityAt: ZonedDateTime,
            applicationDeadline: ZonedDateTime,
            communityId: Long?,
            recruiterUserId: Long,
            status: RecruitmentStatus,
        ): Recruitment = Recruitment(
            capacity = capacity,
            feeAmount = feeAmount,
            activityAt = activityAt,
            applicationDeadline = applicationDeadline,
            communityId = communityId,
            recruiterUserId = recruiterUserId,
            initialStatus = status,
        )
    }
}
