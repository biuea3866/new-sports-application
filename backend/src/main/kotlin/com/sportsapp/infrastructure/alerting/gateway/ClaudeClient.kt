package com.sportsapp.infrastructure.alerting.gateway

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Anthropic Messages API(`POST /v1/messages`) 호출 전용 client (ADR-002, BE-06).
 * [IncidentAnalysisGatewayImpl]에만 DI 한다 — domain·application 레이어에 노출되지 않는다.
 *
 * [ExternalRestClientFactory][com.sportsapp.infrastructure.external.ExternalRestClientFactory]의
 * read timeout(5초)은 LLM 응답(수십 초)엔 부족해, 공유 팩토리를 쓰지 않고 자체 RestClient를
 * [LlmProperties.readTimeoutSeconds]로 구성한다.
 */
@Component
class ClaudeClient(
    private val properties: LlmProperties,
) {

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(properties.baseUrl)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(CONNECT_TIMEOUT)
                setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds))
            },
        )
        .build()

    /** [prompt]를 user 메시지로 담아 Claude Messages API 를 호출한다. */
    fun createMessage(prompt: String): ClaudeMessagesResponse =
        restClient.post()
            .uri(MESSAGES_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .header(HEADER_API_KEY, properties.apiKey)
            .header(HEADER_ANTHROPIC_VERSION, ANTHROPIC_VERSION)
            .body(
                ClaudeMessagesRequest(
                    model = properties.model,
                    maxTokens = properties.maxTokens,
                    messages = listOf(ClaudeMessage(role = ROLE_USER, content = prompt)),
                ),
            )
            .retrieve()
            .body(ClaudeMessagesResponse::class.java)
            ?: ClaudeMessagesResponse(content = emptyList())

    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        private const val MESSAGES_PATH = "/v1/messages"
        private const val HEADER_API_KEY = "x-api-key"
        private const val HEADER_ANTHROPIC_VERSION = "anthropic-version"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val ROLE_USER = "user"
    }
}

data class ClaudeMessagesRequest(
    val model: String,
    @JsonProperty("max_tokens")
    val maxTokens: Int,
    val messages: List<ClaudeMessage>,
)

data class ClaudeMessage(
    val role: String,
    val content: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClaudeMessagesResponse(
    val content: List<ClaudeContentBlock> = emptyList(),
) {
    private companion object {
        private const val TEXT_BLOCK_TYPE = "text"
    }

    /** 첫 번째 `text` 타입 컨텐츠 블록의 본문을 반환한다. 없으면 null. */
    fun text(): String? = content.firstOrNull { it.type == TEXT_BLOCK_TYPE }?.text
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClaudeContentBlock(
    val type: String = "",
    val text: String = "",
)
