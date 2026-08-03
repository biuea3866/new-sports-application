package com.sportsapp.domain.recruitment.service

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.event.ApplicationRefundRequestedEvent
import com.sportsapp.domain.recruitment.policy.CancellationPolicy
import com.sportsapp.domain.recruitment.repository.ApplicationRepository
import com.sportsapp.domain.recruitment.repository.RecruitmentRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.time.ZonedDateTime

private fun liveRecruitment(): Recruitment = Recruitment.create(
    title = "성수 볼더링 초보 클래스",
    capacity = 6,
    feeAmount = BigDecimal("20000"),
    activityAt = ZonedDateTime.now().plusDays(9),
    applicationDeadline = ZonedDateTime.now().plusDays(8),
    communityId = null,
    recruiterUserId = 1L,
)

private fun deletedRecruitment(): Recruitment = Recruitment.create(
    title = "QA재검증 유료모집",
    capacity = 10,
    feeAmount = BigDecimal("10000"),
    activityAt = ZonedDateTime.now().plusDays(6),
    applicationDeadline = ZonedDateTime.now().plusDays(5),
    communityId = null,
    recruiterUserId = 1L,
)

/**
 * 소프트 삭제된 모집에 **이미 성립한 신청**의 생명주기(취소·환불·주문상세)는 계속 동작해야 한다.
 *
 * 삭제된 모집을 발견·신청 대상에서 빼는 것(`findById`)과, 이미 돈이 오간 신청을 정리하는 것은
 * 다른 문제다. 취소 경로가 막히면 사용자가 환불을 못 받는다 — 그래서 이 경로는 삭제 여부와
 * 무관하게 조회하는 `findByIdIncludingDeleted`를 쓴다.
 * (선례: `ApplicationCustomRepositoryImpl`는 같은 상황에서 예외 대신 title을 빈 문자열로 낮춰
 *  목록을 살려둔다 — 신청 생명주기를 끊지 않는다는 같은 원칙)
 *
 * Given 블록마다 mock/서비스를 로컬로 새로 만든다(선례: RecruitmentCancelApplicationDomainServiceTest).
 */
class RecruitmentDeletedRecruitmentLifecycleTest : BehaviorSpec({

    Given("소프트 삭제된 모집에 CONFIRMED 신청을 들고 있는 사용자") {
        val recruitmentRepository = mockk<RecruitmentRepository>()
        val applicationRepository = mockk<ApplicationRepository>()
        val cancellationPolicy = mockk<CancellationPolicy>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = RecruitmentDomainService(
            recruitmentRepository,
            applicationRepository,
            mockk<DistributedLock>(relaxed = true),
            cancellationPolicy,
            domainEventPublisher,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

        val application = Application.create(recruitmentId = 1L, applicantUserId = 100L)
        application.confirm(paymentId = 500L)
        val recruitment = deletedRecruitment()

        every { applicationRepository.findById(1L) } returns application
        // 삭제 필터가 걸린 조회는 null — 취소 경로가 여기에 의존하면 환불이 막힌다.
        every { recruitmentRepository.findById(1L) } returns null
        every { recruitmentRepository.findByIdIncludingDeleted(1L) } returns recruitment
        every { cancellationPolicy.feeRateFor(any()) } returns BigDecimal("0.05")
        every { applicationRepository.save(any()) } answers { firstArg() }
        val capturedEvents = slot<List<DomainEvent>>()
        every { domainEventPublisher.publishAll(capture(capturedEvents)) } answers { Unit }

        When("신청을 취소하면") {
            service.cancelApplication(applicationId = 1L, applicantUserId = 100L)

            Then("취소가 성립하고 환불 이벤트가 발행된다") {
                application.status shouldBe ApplicationStatus.CANCELLED
                val refundEvent = capturedEvents.captured
                    .filterIsInstance<ApplicationRefundRequestedEvent>()
                    .single()
                refundEvent.refundAmount.compareTo(BigDecimal("9500.00")) shouldBe 0
            }
        }
    }

    Given("소프트 삭제된 모집에 신청을 들고 있는 사용자의 주문상세 조회") {
        val recruitmentRepository = mockk<RecruitmentRepository>()
        val applicationRepository = mockk<ApplicationRepository>()
        val service = RecruitmentDomainService(
            recruitmentRepository,
            applicationRepository,
            mockk<DistributedLock>(relaxed = true),
            mockk<CancellationPolicy>(relaxed = true),
            mockk<DomainEventPublisher>(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

        // Application.createdAt은 JPA @CreatedDate(lateinit) — 영속화 전 접근 시 예외가 나므로 스텁한다.
        val application = mockk<Application>(relaxed = true)
        every { application.id } returns 11L
        every { application.recruitmentId } returns 1L
        every { application.status } returns ApplicationStatus.CONFIRMED
        every { application.paymentId } returns 500L
        every { application.createdAt } returns ZonedDateTime.now()

        every { applicationRepository.findById(11L) } returns application
        every { recruitmentRepository.findById(1L) } returns null
        every { recruitmentRepository.findByIdIncludingDeleted(1L) } returns deletedRecruitment()

        When("주문상세를 조회하면") {
            val detail = service.getApplicationDetailBy(applicationId = 11L, requesterUserId = 100L)

            Then("404 대신 신청 상세가 반환된다") {
                detail.applicationId shouldBe 11L
                detail.recruitmentTitle shouldBe "QA재검증 유료모집"
                detail.status shouldBe ApplicationStatus.CONFIRMED
                detail.feeAmount.compareTo(BigDecimal("10000")) shouldBe 0
            }
        }
    }

    Given("살아 있는 모집에 신청을 들고 있는 사용자") {
        val recruitmentRepository = mockk<RecruitmentRepository>()
        val applicationRepository = mockk<ApplicationRepository>()
        val service = RecruitmentDomainService(
            recruitmentRepository,
            applicationRepository,
            mockk<DistributedLock>(relaxed = true),
            mockk<CancellationPolicy>(relaxed = true),
            mockk<DomainEventPublisher>(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

        val application = mockk<Application>(relaxed = true)
        every { application.id } returns 12L
        every { application.recruitmentId } returns 2L
        every { application.status } returns ApplicationStatus.CONFIRMED
        every { application.paymentId } returns 501L
        every { application.createdAt } returns ZonedDateTime.now()

        every { applicationRepository.findById(12L) } returns application
        every { recruitmentRepository.findByIdIncludingDeleted(2L) } returns liveRecruitment()

        When("주문상세를 조회하면") {
            val detail = service.getApplicationDetailBy(applicationId = 12L, requesterUserId = 100L)

            Then("기존과 동일하게 상세가 반환된다") {
                detail.recruitmentTitle shouldBe "성수 볼더링 초보 클래스"
                detail.status shouldBe ApplicationStatus.CONFIRMED
            }
        }
    }
})
