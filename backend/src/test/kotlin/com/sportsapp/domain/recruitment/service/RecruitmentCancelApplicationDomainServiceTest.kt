package com.sportsapp.domain.recruitment.service

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.event.ApplicationRefundRequestedEvent
import com.sportsapp.domain.recruitment.exception.UnauthorizedApplicationAccessException
import com.sportsapp.domain.recruitment.policy.CancellationPolicy
import com.sportsapp.domain.recruitment.repository.ApplicationRepository
import com.sportsapp.domain.recruitment.repository.RecruitmentRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime

private fun recruitmentWithDeadline(deadline: ZonedDateTime): Recruitment = Recruitment.create(
    title = "주말 축구 모임",
    capacity = 10,
    feeAmount = BigDecimal("10000"),
    activityAt = deadline.plusDays(1),
    applicationDeadline = deadline,
    communityId = null,
    recruiterUserId = 1L,
)

/**
 * Given 블록마다 Repository/Lock/Policy/Publisher/Service를 로컬로 새로 만든다 — Kotest BehaviorSpec은
 * 스펙 람다를 한 번만 순차 실행하므로, 최상위(top-level)에서 공유한 mockk는 이후 Given의 verify(exactly=N)
 * 카운트에 이전 Given의 호출이 누적된다(선례: BookingConfirmDomainServiceTest 패턴).
 */
class RecruitmentCancelApplicationDomainServiceTest : BehaviorSpec({

    Given("마감 5일 전 신청 취소 (수수료율 5%)") {
        val recruitmentRepository = mockk<RecruitmentRepository>()
        val applicationRepository = mockk<ApplicationRepository>()
        val distributedLock = mockk<DistributedLock>(relaxed = true)
        val cancellationPolicy = mockk<CancellationPolicy>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = RecruitmentDomainService(recruitmentRepository, applicationRepository, distributedLock, cancellationPolicy, domainEventPublisher, mockk(relaxed = true), mockk(relaxed = true))

        val application = Application.create(recruitmentId = 1L, applicantUserId = 100L)
        application.confirm(paymentId = 500L)
        val recruitment = recruitmentWithDeadline(ZonedDateTime.now().plusDays(5))
        every { applicationRepository.findById(1L) } returns application
        every { recruitmentRepository.findById(1L) } returns recruitment
        every { cancellationPolicy.feeRateFor(any()) } returns BigDecimal("0.05")
        every { applicationRepository.save(any()) } answers { firstArg() }
        val capturedEvents = slot<List<DomainEvent>>()
        every { domainEventPublisher.publishAll(capture(capturedEvents)) } answers { Unit }

        When("cancelApplication(applicationId=1, applicantUserId=100)을 호출하면") {
            service.cancelApplication(applicationId = 1L, applicantUserId = 100L)

            Then("환불액이 참가비의 95%로 계산된 환불 이벤트가 발행된다") {
                application.status shouldBe ApplicationStatus.CANCELLED
                val refundEvent = capturedEvents.captured.filterIsInstance<ApplicationRefundRequestedEvent>().single()
                refundEvent.refundAmount.compareTo(BigDecimal("9500.00")) shouldBe 0
            }
        }
    }

    Given("마감 2일 전 신청 취소 (수수료율 10%)") {
        val recruitmentRepository = mockk<RecruitmentRepository>()
        val applicationRepository = mockk<ApplicationRepository>()
        val distributedLock = mockk<DistributedLock>(relaxed = true)
        val cancellationPolicy = mockk<CancellationPolicy>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = RecruitmentDomainService(recruitmentRepository, applicationRepository, distributedLock, cancellationPolicy, domainEventPublisher, mockk(relaxed = true), mockk(relaxed = true))

        val application = Application.create(recruitmentId = 2L, applicantUserId = 100L)
        application.confirm(paymentId = 501L)
        val recruitment = recruitmentWithDeadline(ZonedDateTime.now().plusDays(2))
        every { applicationRepository.findById(2L) } returns application
        every { recruitmentRepository.findById(2L) } returns recruitment
        every { cancellationPolicy.feeRateFor(any()) } returns BigDecimal("0.10")
        every { applicationRepository.save(any()) } answers { firstArg() }
        val capturedEvents = slot<List<DomainEvent>>()
        every { domainEventPublisher.publishAll(capture(capturedEvents)) } answers { Unit }

        When("cancelApplication(applicationId=2, applicantUserId=100)을 호출하면") {
            service.cancelApplication(applicationId = 2L, applicantUserId = 100L)

            Then("환불액이 참가비의 90%로 계산된 환불 이벤트가 발행된다") {
                val refundEvent = capturedEvents.captured.filterIsInstance<ApplicationRefundRequestedEvent>().single()
                refundEvent.refundAmount.compareTo(BigDecimal("9000.00")) shouldBe 0
            }
        }
    }

    Given("이미 CANCELLED된 신청") {
        val recruitmentRepository = mockk<RecruitmentRepository>()
        val applicationRepository = mockk<ApplicationRepository>()
        val distributedLock = mockk<DistributedLock>(relaxed = true)
        val cancellationPolicy = mockk<CancellationPolicy>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = RecruitmentDomainService(recruitmentRepository, applicationRepository, distributedLock, cancellationPolicy, domainEventPublisher, mockk(relaxed = true), mockk(relaxed = true))

        val application = Application.create(recruitmentId = 3L, applicantUserId = 100L)
        application.confirm(paymentId = 502L)
        val recruitment = recruitmentWithDeadline(ZonedDateTime.now().plusDays(5))
        application.cancelByApplicant(recruitment.applicationDeadline, BigDecimal("9500.00"))
        application.pullDomainEvents()
        every { applicationRepository.findById(3L) } returns application
        every { recruitmentRepository.findById(3L) } returns recruitment
        every { cancellationPolicy.feeRateFor(any()) } returns BigDecimal("0.05")
        every { applicationRepository.save(any()) } answers { firstArg() }
        val capturedEvents = slot<List<DomainEvent>>()
        every { domainEventPublisher.publishAll(capture(capturedEvents)) } answers { Unit }

        When("cancelApplication을 재호출하면") {
            service.cancelApplication(applicationId = 3L, applicantUserId = 100L)

            Then("중복 환불 없이 멱등하게 처리된다") {
                application.status shouldBe ApplicationStatus.CANCELLED
                capturedEvents.captured.size shouldBe 0
            }
        }
    }

    Given("타인 소유의 신청") {
        val recruitmentRepository = mockk<RecruitmentRepository>()
        val applicationRepository = mockk<ApplicationRepository>()
        val distributedLock = mockk<DistributedLock>(relaxed = true)
        val cancellationPolicy = mockk<CancellationPolicy>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = RecruitmentDomainService(recruitmentRepository, applicationRepository, distributedLock, cancellationPolicy, domainEventPublisher, mockk(relaxed = true), mockk(relaxed = true))

        val application = Application.create(recruitmentId = 4L, applicantUserId = 100L)
        every { applicationRepository.findById(4L) } returns application

        When("소유자가 아닌 사용자가 cancelApplication을 호출하면") {
            Then("UnauthorizedApplicationAccessException을 던지고 save가 호출되지 않는다") {
                shouldThrow<UnauthorizedApplicationAccessException> {
                    service.cancelApplication(applicationId = 4L, applicantUserId = 999L)
                }
                verify(exactly = 0) { applicationRepository.save(any()) }
            }
        }
    }

    Given("존재하지 않는 신청") {
        val recruitmentRepository = mockk<RecruitmentRepository>()
        val applicationRepository = mockk<ApplicationRepository>()
        val distributedLock = mockk<DistributedLock>(relaxed = true)
        val cancellationPolicy = mockk<CancellationPolicy>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = RecruitmentDomainService(recruitmentRepository, applicationRepository, distributedLock, cancellationPolicy, domainEventPublisher, mockk(relaxed = true), mockk(relaxed = true))

        every { applicationRepository.findById(5L) } returns null

        When("cancelApplication을 호출하면") {
            Then("ResourceNotFoundException을 던진다") {
                shouldThrow<ResourceNotFoundException> {
                    service.cancelApplication(applicationId = 5L, applicantUserId = 100L)
                }
            }
        }
    }
})
