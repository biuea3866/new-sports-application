package com.sportsapp.presentation.mcp.audit

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.application.mcp.dto.RecordToolInvocationCommand
import com.sportsapp.application.mcp.usecase.RecordToolInvocationUseCase
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

/**
 * audit log 비동기 적재 컴포넌트.
 *
 * Pattern B: AOP 를 사용하지 않고 각 Tool bean 에 직접 주입하여 호출.
 * Spring AI MethodToolCallback 이 CGLIB proxy 를 우회하므로 AOP(@Around) 는 동작하지 않는다.
 *
 * 적재 실패는 tool 호출을 실패시키지 않으며 WARN 로그로만 기록합니다.
 */
@Component
class McpAuditLogAsyncRecorder(
    private val recordToolInvocationUseCase: RecordToolInvocationUseCase,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(McpAuditLogAsyncRecorder::class.java)

    @Async("mcpAuditExecutor")
    fun record(
        tokenId: Long?,
        userId: Long,
        toolName: String,
        namedParams: Map<String, Any?>,
        statusCode: Int,
        latencyMs: Int,
        ipAddr: String?,
        clientUserAgent: String?,
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
                    ipAddr = ipAddr,
                    clientUserAgent = clientUserAgent,
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
