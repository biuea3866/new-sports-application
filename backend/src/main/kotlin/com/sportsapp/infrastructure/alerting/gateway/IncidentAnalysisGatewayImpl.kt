package com.sportsapp.infrastructure.alerting.gateway

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sportsapp.domain.alerting.exception.IncidentAnalysisException
import com.sportsapp.domain.alerting.gateway.IncidentAnalysisGateway
import com.sportsapp.domain.alerting.vo.IncidentAnalysis
import com.sportsapp.domain.alerting.vo.IncidentContext
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException

/**
 * Claude API(Anthropic Messages API)로 원인분석을 수행하는 [IncidentAnalysisGateway] 구현체(ADR-002, BE-06).
 * HTTP 호출은 [ClaudeClient]에만 위임하고, 이 클래스가 프롬프트 구성과 응답→도메인 타입 변환을 담당한다.
 * 타임아웃·5xx·파싱 실패는 모두 [IncidentAnalysisException]으로 변환한다 —
 * 폴백 결정은 [com.sportsapp.domain.alerting.service.AlertDomainService]의 몫이다(FR-8).
 */
@Component
class IncidentAnalysisGatewayImpl(
    private val claudeClient: ClaudeClient,
    private val objectMapper: ObjectMapper,
) : IncidentAnalysisGateway {

    override fun analyze(context: IncidentContext): IncidentAnalysis {
        val response = requestAnalysis(buildPrompt(context))
        return response.toIncidentAnalysis()
    }

    private fun requestAnalysis(prompt: String): ClaudeMessagesResponse = try {
        claudeClient.createMessage(prompt)
    } catch (exception: RestClientException) {
        throw IncidentAnalysisException("Claude API 호출에 실패했습니다: ${exception.message}", exception)
    }

    private fun ClaudeMessagesResponse.toIncidentAnalysis(): IncidentAnalysis {
        val text = text() ?: throw IncidentAnalysisException("Claude 응답에 텍스트 블록이 없습니다")
        val payload = parseAnalysisPayload(text)
        return IncidentAnalysis(
            errorType = payload.errorType,
            causeEstimation = payload.causeEstimation,
            remediation = payload.remediation,
            included = true,
        )
    }

    private fun parseAnalysisPayload(text: String): ClaudeAnalysisPayload = try {
        objectMapper.readValue<ClaudeAnalysisPayload>(text)
    } catch (exception: JsonProcessingException) {
        throw IncidentAnalysisException("Claude 응답 파싱에 실패했습니다: ${exception.message}", exception)
    }

    private fun buildPrompt(context: IncidentContext): String = buildString {
        appendLine("당신은 SRE 어시스턴트입니다. 아래 알람 신호와 텔레메트리를 상관분석해 JSON으로만 답하세요.")
        appendLine("형식: {\"errorType\": string, \"causeEstimation\": string, \"remediation\": string}")
        appendLine("환경(env): ${context.env}")
        appendLine("엔드포인트(endpoint): ${context.signal.endpoint}")
        appendLine("소스(source): ${context.signal.source}")
        appendLine("심각도(severity): ${context.signal.severity}")
        appendLine("메트릭 요약: ${context.snapshot.metricsSummary}")
        appendLine("로그 샘플:")
        context.snapshot.logSamples.forEach { appendLine("- $it") }
        appendLine("트레이스 샘플:")
        context.snapshot.traceSamples.forEach { appendLine("- $it") }
    }
}

private data class ClaudeAnalysisPayload(
    val errorType: String,
    val causeEstimation: String,
    val remediation: String,
)
