package com.sportsapp.application.partner.audit

import com.sportsapp.domain.partner.audit.PartnerAuditLog
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.ZonedDateTime

class PartnerAuditLogResponseTest : BehaviorSpec({

    Given("PartnerAuditLog 도메인 객체가 주어졌을 때") {
        val calledAt = ZonedDateTime.now()
        val auditLog = PartnerAuditLog.reconstitute(
            id = 1L,
            partnerId = 10L,
            userId = 20L,
            httpMethod = "POST",
            requestPath = "/api/goods-seller/products",
            targetResource = "product:1",
            statusCode = 201,
            latencyMs = 30,
            ipAddr = "127.0.0.1",
            clientUserAgent = "test-agent",
            calledAt = calledAt,
        )

        When("from으로 변환하면") {
            val response = PartnerAuditLogResponse.from(auditLog)

            Then("도메인 필드가 응답 필드로 그대로 매핑된다") {
                response.id shouldBe 1L
                response.partnerId shouldBe 10L
                response.userId shouldBe 20L
                response.httpMethod shouldBe "POST"
                response.requestPath shouldBe "/api/goods-seller/products"
                response.targetResource shouldBe "product:1"
                response.statusCode shouldBe 201
                response.latencyMs shouldBe 30
                response.calledAt shouldBe calledAt
            }
        }
    }

    Given("PartnerAuditLog Page가 주어졌을 때") {
        val pageable = PageRequest.of(0, 20)
        val auditLog = PartnerAuditLog.reconstitute(
            id = 2L,
            partnerId = 11L,
            userId = 21L,
            httpMethod = "GET",
            requestPath = "/api/event-host/events",
            targetResource = null,
            statusCode = 200,
            latencyMs = 10,
            ipAddr = null,
            clientUserAgent = null,
            calledAt = ZonedDateTime.now(),
        )
        val page = PageImpl(listOf(auditLog), pageable, 1)

        When("from으로 Page를 변환하면") {
            val responsePage = PartnerAuditLogResponse.from(page)

            Then("각 원소가 PartnerAuditLogResponse로 변환된 Page를 반환한다") {
                responsePage.totalElements shouldBe 1L
                responsePage.content[0].partnerId shouldBe 11L
                responsePage.content[0].httpMethod shouldBe "GET"
            }
        }
    }
})
