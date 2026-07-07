package com.sportsapp.domain.recruitment.service

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.entity.RecruitmentStatus
import com.sportsapp.domain.recruitment.exception.RecruitmentBusyException
import com.sportsapp.domain.recruitment.exception.RecruitmentFullException
import com.sportsapp.domain.recruitment.policy.CancellationPolicy
import com.sportsapp.domain.recruitment.repository.ApplicationRepository
import com.sportsapp.domain.recruitment.repository.RecruitmentRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.Duration
import java.time.ZonedDateTime

private fun recruitmentOf(capacity: Int): Recruitment = Recruitment.create(
    title = "주말 축구 모임",
    capacity = capacity,
    feeAmount = BigDecimal("10000"),
    activityAt = ZonedDateTime.now().plusDays(10),
    applicationDeadline = ZonedDateTime.now().plusDays(5),
    communityId = null,
    recruiterUserId = 1L,
)

/**
 * Given 블록마다 의존성을 로컬로 새로 만든다 — Kotest BehaviorSpec은 스펙 람다를 한 번만 순차 실행하므로,
 * 최상위에서 공유한 mockk는 이후 Given의 verify(exactly=N) 카운트에 이전 Given 호출이 누적된다.
 */
class RecruitmentApplyDomainServiceTest : BehaviorSpec({

    Given("분산락 획득에 실패하는 상황") {
        val recruitmentRepository = mockk<RecruitmentRepository>()
        val applicationRepository = mockk<ApplicationRepository>()
        val distributedLock = mockk<DistributedLock>()
        val cancellationPolicy = mockk<CancellationPolicy>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = RecruitmentDomainService(recruitmentRepository, applicationRepository, distributedLock, cancellationPolicy, domainEventPublisher)

        every { distributedLock.tryLock(any(), any(), any<Duration>()) } returns false

        When("apply를 호출하면") {
            Then("RecruitmentBusyException을 던지고 Application을 생성하지 않는다") {
                shouldThrow<RecruitmentBusyException> {
                    service.apply(recruitmentId = 1L, applicantUserId = 100L)
                }
                verify(exactly = 0) { applicationRepository.save(any()) }
            }
        }
    }

    Given("락 획득 성공 + 정원이 가득 찬 모집") {
        val recruitmentRepository = mockk<RecruitmentRepository>()
        val applicationRepository = mockk<ApplicationRepository>()
        val distributedLock = mockk<DistributedLock>()
        val cancellationPolicy = mockk<CancellationPolicy>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = RecruitmentDomainService(recruitmentRepository, applicationRepository, distributedLock, cancellationPolicy, domainEventPublisher)

        val recruitment = recruitmentOf(capacity = 3)
        every { distributedLock.tryLock(any(), any(), any<Duration>()) } returns true
        every { distributedLock.unlock(any(), any()) } returns true
        every { recruitmentRepository.findForUpdateById(1L) } returns recruitment
        every { applicationRepository.countActiveByRecruitmentId(1L) } returns 3

        When("apply를 호출하면") {
            Then("RecruitmentFullException을 던진다") {
                shouldThrow<RecruitmentFullException> {
                    service.apply(recruitmentId = 1L, applicantUserId = 100L)
                }
                verify(exactly = 0) { applicationRepository.save(any()) }
            }
        }
    }

    Given("락 획득 성공 + 모집 자체가 존재하지 않는 상황") {
        val recruitmentRepository = mockk<RecruitmentRepository>()
        val applicationRepository = mockk<ApplicationRepository>()
        val distributedLock = mockk<DistributedLock>()
        val cancellationPolicy = mockk<CancellationPolicy>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = RecruitmentDomainService(recruitmentRepository, applicationRepository, distributedLock, cancellationPolicy, domainEventPublisher)

        every { distributedLock.tryLock(any(), any(), any<Duration>()) } returns true
        every { distributedLock.unlock(any(), any()) } returns true
        every { recruitmentRepository.findForUpdateById(999L) } returns null

        When("apply를 호출하면") {
            Then("예외 발생 시에도 unlock이 호출된다") {
                shouldThrow<ResourceNotFoundException> {
                    service.apply(recruitmentId = 999L, applicantUserId = 100L)
                }
                verify(exactly = 1) { distributedLock.unlock("recruitment:999", "user:100") }
            }
        }
    }

    Given("정원 여유가 있는 모집") {
        val recruitmentRepository = mockk<RecruitmentRepository>()
        val applicationRepository = mockk<ApplicationRepository>()
        val distributedLock = mockk<DistributedLock>()
        val cancellationPolicy = mockk<CancellationPolicy>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = RecruitmentDomainService(recruitmentRepository, applicationRepository, distributedLock, cancellationPolicy, domainEventPublisher)

        val recruitment = recruitmentOf(capacity = 5)
        every { distributedLock.tryLock(any(), any(), any<Duration>()) } returns true
        every { distributedLock.unlock(any(), any()) } returns true
        every { recruitmentRepository.findForUpdateById(1L) } returns recruitment
        every { applicationRepository.countActiveByRecruitmentId(1L) } returns 2
        every { applicationRepository.save(any()) } answers { firstArg() }
        every { recruitmentRepository.save(any()) } answers { firstArg() }

        When("apply를 호출하면") {
            val application = service.apply(recruitmentId = 1L, applicantUserId = 100L)

            Then("PENDING 상태의 Application이 생성되고 정원 미달이라 CLOSED로 전이되지 않는다") {
                application.status shouldBe ApplicationStatus.PENDING
                application.recruitmentId shouldBe 1L
                application.applicantUserId shouldBe 100L
                recruitment.status shouldBe RecruitmentStatus.OPEN
            }
        }
    }

    Given("정원이 3명이고 현재 2명이 신청한 모집에 마지막 1자리 신청") {
        val recruitmentRepository = mockk<RecruitmentRepository>()
        val applicationRepository = mockk<ApplicationRepository>()
        val distributedLock = mockk<DistributedLock>()
        val cancellationPolicy = mockk<CancellationPolicy>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = RecruitmentDomainService(recruitmentRepository, applicationRepository, distributedLock, cancellationPolicy, domainEventPublisher)

        val recruitment = recruitmentOf(capacity = 3)
        every { distributedLock.tryLock(any(), any(), any<Duration>()) } returns true
        every { distributedLock.unlock(any(), any()) } returns true
        every { recruitmentRepository.findForUpdateById(2L) } returns recruitment
        every { applicationRepository.countActiveByRecruitmentId(2L) } returns 2
        every { applicationRepository.save(any()) } answers { firstArg() }
        every { recruitmentRepository.save(any()) } answers { firstArg() }

        When("apply를 호출하면") {
            service.apply(recruitmentId = 2L, applicantUserId = 200L)

            Then("정원이 충족되어 모집이 CLOSED로 전이된다") {
                recruitment.status shouldBe RecruitmentStatus.CLOSED
                verify(exactly = 1) { recruitmentRepository.save(recruitment) }
            }
        }
    }
})
