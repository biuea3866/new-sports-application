package com.sportsapp.application.recruitment.dto

import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

/**
 * 신청자 목록 화면(A-R4)은 "누가 신청했는가"를 보여줘야 하는데, 응답에 신청자 식별자가 없어
 * 신청 행 PK(`신청 #5`)를 대신 노출하고 있었다. 응답이 신청자 식별자를 싣는지 검증한다.
 */
class ApplicationResponseTest : BehaviorSpec({

    Given("신청 엔티티") {
        // Application.createdAt은 JPA @CreatedDate(lateinit) — 영속화 전 접근 시 예외가 나므로 스텁한다.
        val appliedAt = ZonedDateTime.now()
        val application = mockk<Application>(relaxed = true)
        every { application.id } returns 100L
        every { application.recruitmentId } returns 1L
        every { application.applicantUserId } returns 71L
        every { application.status } returns ApplicationStatus.CONFIRMED
        every { application.paymentId } returns 200L
        every { application.createdAt } returns appliedAt

        When("응답으로 변환하면") {
            val response = ApplicationResponse.of(application)

            Then("신청자 식별자가 포함된다") {
                response.applicantUserId shouldBe 71L
            }

            Then("기존 필드도 그대로 유지된다") {
                response.id shouldBe 100L
                response.recruitmentId shouldBe 1L
                response.status shouldBe ApplicationStatus.CONFIRMED
                response.paymentId shouldBe 200L
                response.appliedAt shouldBe appliedAt
            }
        }
    }
})
