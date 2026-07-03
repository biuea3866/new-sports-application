package com.sportsapp.application.partner.audit

import com.sportsapp.domain.partner.audit.PartnerAuditLog
import com.sportsapp.domain.partner.audit.PartnerAuditLogDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.ZonedDateTime

class ListPartnerAuditLogsUseCaseTest : BehaviorSpec({

    val partnerAuditLogDomainService = mockk<PartnerAuditLogDomainService>()
    val useCase = ListPartnerAuditLogsUseCase(partnerAuditLogDomainService)

    Given("파트너의 감사 로그가 존재할 때") {
        val partnerId = 1L
        val from = ZonedDateTime.now().minusDays(7)
        val to = ZonedDateTime.now()
        val pageable: Pageable = PageRequest.of(0, 20)
        val command = ListPartnerAuditLogsCommand.of(partnerId, from, to, pageable)

        val auditLog = PartnerAuditLog.reconstitute(
            id = 100L,
            partnerId = partnerId,
            userId = 5L,
            httpMethod = "GET",
            requestPath = "/api/goods-seller/products",
            targetResource = "product:1",
            statusCode = 200,
            latencyMs = 15,
            ipAddr = "127.0.0.1",
            clientUserAgent = "test-agent",
            calledAt = to,
        )
        val page = PageImpl(listOf(auditLog), pageable, 1)
        every { partnerAuditLogDomainService.listBy(partnerId, from, to, pageable) } returns page

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("PartnerAuditLogDomainService.listBy를 정확한 인자로 호출한다") {
                verify(exactly = 1) { partnerAuditLogDomainService.listBy(partnerId, from, to, pageable) }
            }

            Then("조회된 Page를 PartnerAuditLogResponse Page로 변환해 반환한다") {
                result.totalElements shouldBe 1L
                result.content[0].partnerId shouldBe partnerId
                result.content[0].httpMethod shouldBe "GET"
                result.content[0].statusCode shouldBe 200
            }
        }
    }

    Given("신규 파트너의 감사 로그가 없을 때") {
        val partnerId = 2L
        val from = ZonedDateTime.now().minusDays(7)
        val to = ZonedDateTime.now()
        val pageable: Pageable = PageRequest.of(0, 20)
        val command = ListPartnerAuditLogsCommand.of(partnerId, from, to, pageable)
        val emptyPage = PageImpl<PartnerAuditLog>(emptyList(), pageable, 0)
        every { partnerAuditLogDomainService.listBy(partnerId, from, to, pageable) } returns emptyPage

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("빈 Page를 반환한다") {
                result.totalElements shouldBe 0L
                result.content shouldBe emptyList()
            }
        }
    }
})
