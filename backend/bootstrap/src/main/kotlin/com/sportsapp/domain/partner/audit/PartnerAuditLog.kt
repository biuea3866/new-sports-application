package com.sportsapp.domain.partner.audit

import java.time.ZonedDateTime

class PartnerAuditLog private constructor(
    val id: Long?,
    val partnerId: Long,
    val userId: Long,
    val httpMethod: String,
    val requestPath: String,
    val targetResource: String?,
    val statusCode: Int,
    val latencyMs: Int,
    val ipAddr: String?,
    val clientUserAgent: String?,
    val calledAt: ZonedDateTime,
) {
    companion object {
        fun of(
            partnerId: Long,
            userId: Long,
            httpMethod: String,
            requestPath: String,
            targetResource: String?,
            statusCode: Int,
            latencyMs: Int,
            ipAddr: String?,
            clientUserAgent: String?,
            calledAt: ZonedDateTime,
        ): PartnerAuditLog {
            require(httpMethod.isNotBlank()) { "httpMethod must not be blank" }
            require(requestPath.isNotBlank()) { "requestPath must not be blank" }
            return PartnerAuditLog(
                id = null,
                partnerId = partnerId,
                userId = userId,
                httpMethod = httpMethod,
                requestPath = requestPath,
                targetResource = targetResource,
                statusCode = statusCode,
                latencyMs = latencyMs,
                ipAddr = ipAddr,
                clientUserAgent = clientUserAgent,
                calledAt = calledAt,
            )
        }

        fun reconstitute(
            id: Long,
            partnerId: Long,
            userId: Long,
            httpMethod: String,
            requestPath: String,
            targetResource: String?,
            statusCode: Int,
            latencyMs: Int,
            ipAddr: String?,
            clientUserAgent: String?,
            calledAt: ZonedDateTime,
        ): PartnerAuditLog = PartnerAuditLog(
            id = id,
            partnerId = partnerId,
            userId = userId,
            httpMethod = httpMethod,
            requestPath = requestPath,
            targetResource = targetResource,
            statusCode = statusCode,
            latencyMs = latencyMs,
            ipAddr = ipAddr,
            clientUserAgent = clientUserAgent,
            calledAt = calledAt,
        )
    }
}
