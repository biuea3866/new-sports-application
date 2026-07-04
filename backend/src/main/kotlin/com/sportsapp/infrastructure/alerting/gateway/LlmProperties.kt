package com.sportsapp.infrastructure.alerting.gateway

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Claude API(Anthropic Messages API) 호출 설정 (ADR-002, BE-06).
 * [apiKey]는 `ALERTING_LLM_API_KEY` 환경변수로 주입한다(BE-10 담당, 비커밋).
 * [readTimeoutSeconds]는 NFR 60초 이내로 [ExternalRestClientFactory]의 5초 고정 read timeout보다 길게 구성한다.
 */
@ConfigurationProperties(prefix = "alerting.llm")
data class LlmProperties(
    val baseUrl: String = "https://api.anthropic.com",
    val apiKey: String = "",
    val model: String = "claude-fable-5",
    val readTimeoutSeconds: Long = 55L,
    val maxTokens: Int = 1024,
)
