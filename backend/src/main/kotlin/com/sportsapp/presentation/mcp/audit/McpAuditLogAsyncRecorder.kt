package com.sportsapp.presentation.mcp.audit

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.application.mcp.RecordToolInvocationCommand
import com.sportsapp.application.mcp.RecordToolInvocationUseCase
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

/**
 * audit log 비동기 적재 컴포넌트.
 *
 * @Async 메서드는 Spring AOP proxy 가 적용되려면 별도 빈이어야 합니다.
 * McpToolAuditAspect 와 같은 빈에 @Async 를 선언하면 self-invocation 문제가 발생하므로
 * 이 클래스에 분리합니다.
 *
 * 적재 실패는 tool 호출을 실패시키지 않으며 WARN 로그로만 기록합니다.
 */
@Component
class McpAuditLogAsyncRecorder(
    private val recordToolInvocationUseCase: RecordToolInvocationUseCase,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(McpAuditLogAsyncRecorder::class.java)

    @Async
    fun record(
        tokenId: Long?,
        userId: Long,
        toolName: String,
        namedParams: Map<String, Any?>,
        statusCode: Int,
        latencyMs: Int,
        calledAt: ZonedDateTime,
    ) {
        try {
            val paramsMasked = ToolParamsMasker.mask(namedParams, objectMapper)
            recordToolInvocationUseCase.execute(
                RecordToolInvocationCommand(
                    tokenId = tokenId,
                    userId = userId,
                    toolName = toolName,
                    paramsMasked = paramsMasked,
                    statusCode = statusCode,
                    latencyMs = latencyMs,
                    ipAddr = null,
                    clientUserAgent = null,
                    calledAt = calledAt,
                ),
            )
        } catch (exception: Exception) {
            logger.warn(
                "[BE-17] audit log 적재 실패 — toolName={}, userId={}: {}",
                toolName,
                userId,
                exception.message,
            )
        }
    }
}
