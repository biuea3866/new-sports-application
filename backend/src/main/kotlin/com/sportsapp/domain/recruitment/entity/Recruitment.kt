package com.sportsapp.domain.recruitment.entity

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.recruitment.exception.InvalidRecruitmentException
import com.sportsapp.domain.recruitment.exception.InvalidRecruitmentStateException
import com.sportsapp.domain.recruitment.exception.NotRecruiterException
import com.sportsapp.domain.recruitment.exception.RecruitmentApplicationClosedException
import com.sportsapp.domain.recruitment.exception.RecruitmentFullException
import com.sportsapp.domain.recruitment.exception.RecruitmentNotOpenException
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
    @Column(name = "title", nullable = false)
    val title: String,

    @Column(name = "description", nullable = true)
    val description: String?,

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

    /**
     * 신청 가능 여부를 자기 상태로 판단해 검증한다. 모집중(OPEN)이 아니거나, 마감이 지났거나,
     * 정원이 가득 찼으면 각 사유에 맞는 예외를 던진다.
     */
    fun requireApplicable(currentApplicantCount: Int) {
        if (status != RecruitmentStatus.OPEN) throw RecruitmentNotOpenException(id, status)
        if (isDeadlinePassed()) throw RecruitmentApplicationClosedException(id)
        if (currentApplicantCount >= capacity) throw RecruitmentFullException(id)
    }

    fun isFree(): Boolean = feeAmount.signum() == 0

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
        private const val TITLE_MAX_LENGTH = 200

        fun create(
            title: String,
            capacity: Int,
            feeAmount: BigDecimal,
            activityAt: ZonedDateTime,
            applicationDeadline: ZonedDateTime,
            communityId: Long?,
            recruiterUserId: Long,
            description: String? = null,
        ): Recruitment {
            validateTitle(title)
            validateCapacity(capacity)
            validateFeeAmount(feeAmount)
            return Recruitment(
                title = title,
                description = description,
                capacity = capacity,
                feeAmount = feeAmount,
                activityAt = activityAt,
                applicationDeadline = applicationDeadline,
                communityId = communityId,
                recruiterUserId = recruiterUserId,
                initialStatus = RecruitmentStatus.OPEN,
            )
        }

        private fun validateTitle(title: String) {
            if (title.isBlank()) {
                throw InvalidRecruitmentException("title must not be blank")
            }
            if (title.length > TITLE_MAX_LENGTH) {
                throw InvalidRecruitmentException("title must not exceed $TITLE_MAX_LENGTH characters, got: ${title.length}")
            }
        }

        private fun validateCapacity(capacity: Int) {
            if (capacity <= 0) {
                throw InvalidRecruitmentException("capacity must be positive, got: $capacity")
            }
        }

        private fun validateFeeAmount(feeAmount: BigDecimal) {
            if (feeAmount < BigDecimal.ZERO) {
                throw InvalidRecruitmentException("feeAmount must not be negative, got: $feeAmount")
            }
        }

        fun reconstitute(
            title: String,
            description: String?,
            capacity: Int,
            feeAmount: BigDecimal,
            activityAt: ZonedDateTime,
            applicationDeadline: ZonedDateTime,
            communityId: Long?,
            recruiterUserId: Long,
            status: RecruitmentStatus,
        ): Recruitment = Recruitment(
            title = title,
            description = description,
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
