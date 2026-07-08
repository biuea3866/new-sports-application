package com.sportsapp.infrastructure.partner.audit

import com.sportsapp.domain.partner.audit.PartnerAuditLog
import com.sportsapp.domain.partner.audit.PartnerAuditLogDomainService
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.ZonedDateTime

class AsyncPartnerActivityRecorderTest : BehaviorSpec({

    Given("PartnerAuditLogDomainService가 정상 동작한다") {
        val partnerAuditLogDomainService = mockk<PartnerAuditLogDomainService>()
        val recorder = AsyncPartnerActivityRecorder(partnerAuditLogDomainService)
        val auditLogSlot = slot<PartnerAuditLog>()
        every { partnerAuditLogDomainService.record(capture(auditLogSlot)) } answers { auditLogSlot.captured }
        val calledAt = ZonedDateTime.now()

        When("record를 호출하면") {
            recorder.record(
                partnerId = 1L,
                userId = 10L,
                httpMethod = "POST",
                requestPath = "/api/goods-seller/products",
                statusCode = 201,
                latencyMs = 120,
                ipAddr = "127.0.0.1",
                userAgent = "partner-client/1.0",
                calledAt = calledAt,
            )

            Then("PartnerAuditLogDomainService.record가 요청 필드로 1회 호출된다") {
                verify(exactly = 1) { partnerAuditLogDomainService.record(any()) }
                auditLogSlot.captured.partnerId shouldBe 1L
                auditLogSlot.captured.userId shouldBe 10L
                auditLogSlot.captured.httpMethod shouldBe "POST"
                auditLogSlot.captured.requestPath shouldBe "/api/goods-seller/products"
                auditLogSlot.captured.statusCode shouldBe 201
                auditLogSlot.captured.latencyMs shouldBe 120
                auditLogSlot.captured.ipAddr shouldBe "127.0.0.1"
                auditLogSlot.captured.clientUserAgent shouldBe "partner-client/1.0"
                auditLogSlot.captured.calledAt shouldBe calledAt
            }
        }
    }

    Given("PartnerAuditLogDomainService가 예외를 던진다") {
        val partnerAuditLogDomainService = mockk<PartnerAuditLogDomainService>()
        val recorder = AsyncPartnerActivityRecorder(partnerAuditLogDomainService)
        every { partnerAuditLogDomainService.record(any()) } throws RuntimeException("DB 순단")

        When("record를 호출하면") {
            Then("예외를 전파하지 않는다") {
                shouldNotThrowAny {
                    recorder.record(
                        partnerId = 1L,
                        userId = 10L,
                        httpMethod = "POST",
                        requestPath = "/api/goods-seller/products",
                        statusCode = 201,
                        latencyMs = 120,
                        ipAddr = null,
                        userAgent = null,
                        calledAt = ZonedDateTime.now(),
                    )
                }
            }
        }
    }
})
