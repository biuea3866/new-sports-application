package com.sportsapp.domain.mcp.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class McpAuditLogTest : BehaviorSpec({

    Given("token_id null McpAuditLog") {
        val calledAt = ZonedDateTime.now()

        When("McpAuditLog() constructor called with tokenId=null") {
            val auditLog = McpAuditLog(
                tokenId = null,
                userId = 42L,
                toolName = "getBookings",
                paramsMasked = null,
                statusCode = 200,
                latencyMs = 120,
                clientUserAgent = null,
                ipAddr = null,
                asn = null,
                calledAt = calledAt,
            )

            Then("[U-01] tokenId null McpAuditLog is created successfully") {
                auditLog.tokenId.shouldBeNull()
                auditLog.userId shouldBe 42L
                auditLog.toolName shouldBe "getBookings"
                auditLog.statusCode shouldBe 200
                auditLog.latencyMs shouldBe 120
                auditLog.calledAt shouldBe calledAt
            }
        }
    }

    Given("McpAuditLog with all fields") {
        val auditLog = McpAuditLog(
            tokenId = 1L,
            userId = 10L,
            toolName = "createSlot",
            paramsMasked = """{"facilityId":"FAC-01"}""",
            statusCode = 201,
            latencyMs = 55,
            clientUserAgent = "mcp-client/1.0",
            ipAddr = "192.168.1.1",
            asn = "AS12345",
            calledAt = ZonedDateTime.now(),
        )

        Then("[U-02] McpAuditLog has no mutable public methods (immutable structure)") {
            val publicMethods = McpAuditLog::class.java.declaredMethods
                .filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
                .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .map { it.name }
                .filter { name ->
                    !name.startsWith("get") &&
                        !name.startsWith("is") &&
                        !name.startsWith("access\$") &&
                        name !in setOf("equals", "hashCode", "toString")
                }

            publicMethods shouldBe emptyList()

            auditLog.tokenId shouldBe 1L
            auditLog.userId shouldBe 10L
            auditLog.toolName shouldBe "createSlot"
            auditLog.paramsMasked shouldBe """{"facilityId":"FAC-01"}"""
            auditLog.statusCode shouldBe 201
            auditLog.latencyMs shouldBe 55
            auditLog.clientUserAgent shouldBe "mcp-client/1.0"
            auditLog.ipAddr shouldBe "192.168.1.1"
            auditLog.asn shouldBe "AS12345"
        }
    }
})
