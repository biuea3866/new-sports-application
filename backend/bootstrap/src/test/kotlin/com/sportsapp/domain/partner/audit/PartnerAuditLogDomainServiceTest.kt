package com.sportsapp.domain.partner.audit

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.ZonedDateTime

class PartnerAuditLogDomainServiceTest : BehaviorSpec({

    val partnerAuditLogRepository = mockk<PartnerAuditLogRepository>()
    val partnerAuditLogCustomRepository = mockk<PartnerAuditLogCustomRepository>()
    val service = PartnerAuditLogDomainService(partnerAuditLogRepository, partnerAuditLogCustomRepository)

    fun createAuditLog(): PartnerAuditLog = PartnerAuditLog.of(
        partnerId = 1L,
        userId = 10L,
        httpMethod = "GET",
        requestPath = "/api/partner/v1/products",
        targetResource = null,
        statusCode = 200,
        latencyMs = 15,
        ipAddr = null,
        clientUserAgent = null,
        calledAt = ZonedDateTime.now(),
    )

    Given("감사 기록 요청이 주어지면") {
        val auditLog = createAuditLog()
        every { partnerAuditLogRepository.save(auditLog) } returns auditLog

        When("record를 호출하면") {
            val result = service.record(auditLog)

            Then("PartnerAuditLogRepository.save가 1회 호출된다") {
                verify(exactly = 1) { partnerAuditLogRepository.save(auditLog) }
                result shouldBe auditLog
            }
        }
    }

    Given("특정 파트너·기간의 감사 목록 조회 요청이 주어지면") {
        val partnerId = 1L
        val from = ZonedDateTime.now().minusDays(7)
        val to = ZonedDateTime.now()
        val pageable = PageRequest.of(0, 20)
        val expectedPage: Page<PartnerAuditLog> = PageImpl(listOf(createAuditLog()))
        every { partnerAuditLogCustomRepository.findBy(partnerId, from, to, pageable) } returns expectedPage

        When("listBy를 호출하면") {
            val result = service.listBy(partnerId, from, to, pageable)

            Then("PartnerAuditLogCustomRepository.findBy에 위임한 Page를 그대로 반환한다") {
                result shouldBe expectedPage
                verify(exactly = 1) { partnerAuditLogCustomRepository.findBy(partnerId, from, to, pageable) }
            }
        }
    }
})
