package com.sportsapp.presentation.mcp.audit

import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

/**
 * MCP tool 호출 audit log 적재 Aspect.
 *
 * presentation layer — 외부 진입점(tool 호출) 횡단 관심사 처리.
 * @Tool 어노테이션이 붙은 메서드를 @Around 로 가로채어 호출 결과와 관계없이 audit log 를 적재합니다.
 *
 * 실제 적재는 McpAuditLogAsyncRecorder(@Async) 에 위임하여 tool 응답 시간에 영향을 주지 않습니다.
 *
 * [BE-17] AOP 패턴(A) 채택 — Spring AI @Tool 메서드를 직접 가로채는 방식.
 */
@Aspect
@Component
class McpToolAuditAspect(
    private val mcpAuditLogAsyncRecorder: McpAuditLogAsyncRecorder,
) {

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    fun aroundToolInvocation(joinPoint: ProceedingJoinPoint): Any? {
        val toolName = joinPoint.signature.name
        val calledAt = ZonedDateTime.now()
        val startNano = System.nanoTime()
        val (tokenId, userId) = extractPrincipal()
        val namedParams = buildNamedParams(joinPoint)

        var statusCode = 200
        return try {
            joinPoint.proceed().also {
                statusCode = 200
            }
        } catch (exception: RuntimeException) {
            statusCode = 500
            throw exception
        } finally {
            val latencyMs = ((System.nanoTime() - startNano) / 1_000_000).toInt()
            mcpAuditLogAsyncRecorder.record(
                tokenId = tokenId,
                userId = userId,
                toolName = toolName,
                namedParams = namedParams,
                statusCode = statusCode,
                latencyMs = latencyMs,
                calledAt = calledAt,
            )
        }
    }

    private fun extractPrincipal(): Pair<Long?, Long> {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        return if (principal is McpAuthenticatedPrincipal) {
            principal.tokenId to principal.userId
        } else {
            null to 0L
        }
    }

    private fun buildNamedParams(joinPoint: ProceedingJoinPoint): Map<String, Any?> {
        val paramNames = (joinPoint.signature as? MethodSignature)?.parameterNames ?: emptyArray()
        val args = joinPoint.args
        return if (paramNames.isEmpty()) {
            args.mapIndexed { index, value -> "arg$index" to value }.toMap()
        } else {
            paramNames.zip(args).toMap()
        }
    }
}
