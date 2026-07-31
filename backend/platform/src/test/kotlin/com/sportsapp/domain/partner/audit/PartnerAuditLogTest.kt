package com.sportsapp.domain.partner.audit

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import java.time.ZonedDateTime

class PartnerAuditLogTest : BehaviorSpec({

    fun createAuditLog(calledAt: ZonedDateTime = ZonedDateTime.now()): PartnerAuditLog = PartnerAuditLog.of(
        partnerId = 1L,
        userId = 10L,
        httpMethod = "POST",
        requestPath = "/api/partner/v1/products",
        targetResource = "product-123",
        statusCode = 201,
        latencyMs = 42,
        ipAddr = "127.0.0.1",
        clientUserAgent = "partner-sdk/1.0",
        calledAt = calledAt,
    )

    Given("필수값을 모두 갖춘 요청으로 PartnerAuditLog를 생성하면") {
        val auditLog = createAuditLog()

        Then("statusCode·latencyMs를 그대로 담는다") {
            auditLog.statusCode shouldBe 201
            auditLog.latencyMs shouldBe 42
        }

        Then("partnerId·userId·httpMethod·requestPath를 그대로 담는다") {
            auditLog.partnerId shouldBe 1L
            auditLog.userId shouldBe 10L
            auditLog.httpMethod shouldBe "POST"
            auditLog.requestPath shouldBe "/api/partner/v1/products"
        }

        Then("id는 아직 없다") {
            auditLog.id.shouldBeNull()
        }
    }

    Given("targetResource·ipAddr·clientUserAgent가 없는 요청으로 생성하면") {
        val auditLog = PartnerAuditLog.of(
            partnerId = 1L,
            userId = 10L,
            httpMethod = "GET",
            requestPath = "/api/partner/v1/products",
            targetResource = null,
            statusCode = 200,
            latencyMs = 10,
            ipAddr = null,
            clientUserAgent = null,
            calledAt = ZonedDateTime.now(),
        )

        Then("nullable 필드는 null로 유지된다") {
            auditLog.targetResource.shouldBeNull()
            auditLog.ipAddr.shouldBeNull()
            auditLog.clientUserAgent.shouldBeNull()
        }
    }

    Given("전달받은 calledAt으로 PartnerAuditLog를 생성하면") {
        val calledAt = ZonedDateTime.now().minusMinutes(5)
        val auditLog = createAuditLog(calledAt = calledAt)

        Then("calledAt을 그대로 보존한다") {
            auditLog.calledAt shouldBe calledAt
        }
    }

    Given("httpMethod가 빈 문자열인 요청으로 생성하면") {
        Then("IllegalArgumentException이 발생한다") {
            shouldThrow<IllegalArgumentException> {
                createAuditLog().let {
                    PartnerAuditLog.of(
                        partnerId = 1L,
                        userId = 10L,
                        httpMethod = "",
                        requestPath = "/api/partner/v1/products",
                        targetResource = null,
                        statusCode = 200,
                        latencyMs = 10,
                        ipAddr = null,
                        clientUserAgent = null,
                        calledAt = ZonedDateTime.now(),
                    )
                }
            }
        }
    }

    Given("requestPath가 빈 문자열인 요청으로 생성하면") {
        Then("IllegalArgumentException이 발생한다") {
            shouldThrow<IllegalArgumentException> {
                PartnerAuditLog.of(
                    partnerId = 1L,
                    userId = 10L,
                    httpMethod = "GET",
                    requestPath = "",
                    targetResource = null,
                    statusCode = 200,
                    latencyMs = 10,
                    ipAddr = null,
                    clientUserAgent = null,
                    calledAt = ZonedDateTime.now(),
                )
            }
        }
    }
})
