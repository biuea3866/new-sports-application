package com.sportsapp.domain.recruitment.service

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.entity.RecruitmentStatus
import com.sportsapp.domain.recruitment.event.ApplicationRefundRequestedEvent
import com.sportsapp.domain.recruitment.exception.NotRecruiterException
import com.sportsapp.domain.recruitment.policy.CancellationPolicy
import com.sportsapp.domain.recruitment.repository.ApplicationRepository
import com.sportsapp.domain.recruitment.repository.RecruitmentRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.time.ZonedDateTime

private fun openRecruitment(recruiterUserId: Long = 1L): Recruitment = Recruitment.create(
    title = "주말 축구 모임",
    capacity = 10,
    feeAmount = BigDecimal("10000"),
    activityAt = ZonedDateTime.now().plusDays(10),
    applicationDeadline = ZonedDateTime.now().plusDays(5),
    communityId = null,
    recruiterUserId = recruiterUserId,
)

class RecruitmentCancelRecruitmentDomainServiceTest : BehaviorSpec({

    val recruitmentRepository = mockk<RecruitmentRepository>()
    val applicationRepository = mockk<ApplicationRepository>()
    val distributedLock = mockk<DistributedLock>(relaxed = true)
    val cancellationPolicy = mockk<CancellationPolicy>()
    val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)

    val service = RecruitmentDomainService(
        recruitmentRepository,
        applicationRepository,
        distributedLock,
        cancellationPolicy,
        domainEventPublisher,
        mockk(relaxed = true),
        mockk(relaxed = true),
    )

    Given("CONFIRMED 신청 2건이 있는 모집을 개설자가 취소하는 상황") {
        val recruitment = openRecruitment(recruiterUserId = 1L)
        val confirmed1 = Application.create(recruitmentId = recruitment.id, applicantUserId = 100L)
        confirmed1.confirm(paymentId = 1000L)
        val confirmed2 = Application.create(recruitmentId = recruitment.id, applicantUserId = 200L)
        confirmed2.confirm(paymentId = 1001L)
        every { recruitmentRepository.findById(recruitment.id) } returns recruitment
        every { recruitmentRepository.save(any()) } answers { firstArg() }
        every { applicationRepository.findConfirmedByRecruitmentId(recruitment.id) } returns listOf(confirmed1, confirmed2)
        every { applicationRepository.save(confirmed1) } answers { firstArg() }
        every { applicationRepository.save(confirmed2) } answers { firstArg() }
        val capturedEvents = slot<List<DomainEvent>>()
        every { domainEventPublisher.publishAll(capture(capturedEvents)) } answers { Unit }

        When("cancelRecruitment(recruitmentId, recruiterUserId=1)을 호출하면") {
            service.cancelRecruitment(recruitmentId = recruitment.id, recruiterUserId = 1L)

            Then("모집이 CANCELLED로 전이되고 CONFIRMED 전원이 참가비 전액환불 이벤트를 받는다") {
                recruitment.status shouldBe RecruitmentStatus.CANCELLED
                confirmed1.status shouldBe ApplicationStatus.CANCELLED
                confirmed2.status shouldBe ApplicationStatus.CANCELLED
                val refundEvents = capturedEvents.captured.filterIsInstance<ApplicationRefundRequestedEvent>()
                refundEvents.size shouldBe 2
                refundEvents.forEach { it.refundAmount.compareTo(BigDecimal("10000")) shouldBe 0 }
                refundEvents.forEach { it.reason shouldBe "RECRUITMENT_CANCELLED" }
            }
        }
    }

    Given("개설자가 아닌 사용자가 취소를 시도하는 상황") {
        val recruitment = openRecruitment(recruiterUserId = 1L)
        every { recruitmentRepository.findById(recruitment.id) } returns recruitment

        When("cancelRecruitment(recruitmentId, recruiterUserId=99)를 호출하면") {
            Then("NotRecruiterException을 던지고 환불 이벤트가 발행되지 않는다") {
                shouldThrow<NotRecruiterException> {
                    service.cancelRecruitment(recruitmentId = recruitment.id, recruiterUserId = 99L)
                }
                recruitment.status shouldBe RecruitmentStatus.OPEN
            }
        }
    }

    Given("이미 CANCELLED된 모집에 재취소가 호출되는 상황") {
        val recruitment = openRecruitment(recruiterUserId = 1L)
        recruitment.cancelByHost(1L)
        every { recruitmentRepository.findById(recruitment.id) } returns recruitment
        every { recruitmentRepository.save(any()) } answers { firstArg() }
        every { applicationRepository.findConfirmedByRecruitmentId(recruitment.id) } returns emptyList()
        val capturedEvents = slot<List<DomainEvent>>()
        every { domainEventPublisher.publishAll(capture(capturedEvents)) } answers { Unit }

        When("cancelRecruitment을 재호출하면") {
            service.cancelRecruitment(recruitmentId = recruitment.id, recruiterUserId = 1L)

            Then("중복 환불 없이 멱등하게 처리된다") {
                recruitment.status shouldBe RecruitmentStatus.CANCELLED
                capturedEvents.captured.size shouldBe 0
            }
        }
    }
})
