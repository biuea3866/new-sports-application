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
        // LongParameterList 억제 근거(W1-DEBT-01): 파라미터가 영속 테이블 컬럼과 1:1 대응하는 복원 팩토리다.
        // 값 객체로 묶으면 영속 매핑·상태 전이를 함께 건드려야 해 정적 분석 정리 범위를 벗어난다
        // (후속 리팩토링 티켓 대상). 호출부는 named argument 를 강제해 인자 뒤바뀜 위험을 이미 막고 있다.
        @Suppress("LongParameterList")
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

        // LongParameterList 억제 근거(W1-DEBT-01): 파라미터가 영속 테이블 컬럼과 1:1 대응하는 복원 팩토리다.
        // 값 객체로 묶으면 영속 매핑·상태 전이를 함께 건드려야 해 정적 분석 정리 범위를 벗어난다
        // (후속 리팩토링 티켓 대상). 호출부는 named argument 를 강제해 인자 뒤바뀜 위험을 이미 막고 있다.
        @Suppress("LongParameterList")
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
