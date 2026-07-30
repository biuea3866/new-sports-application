package com.sportsapp.presentation.mcp.dto.response

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class McpErrorPayloadTest : BehaviorSpec({

    val objectMapper = ObjectMapper().registerKotlinModule()

    Given("suggested_params와 recoverable이 있는 McpErrorPayload") {
        val payload = McpErrorPayload(
            code = "RESPONSE_TOO_LARGE",
            message = "응답 크기가 256KB를 초과합니다.",
            recoverable = true,
            suggestedAction = "page_size를 줄여서 재호출하세요.",
            suggestedParams = mapOf("page_size" to 85),
        )

        Then("recoverable boolean이 정상 직렬화된다") {
            val json = objectMapper.writeValueAsString(payload)
            json shouldContain "\"recoverable\":true"
        }

        Then("suggested_params가 snake_case로 직렬화된다") {
            val json = objectMapper.writeValueAsString(payload)
            json shouldContain "\"suggested_params\""
            json shouldNotContain "\"suggestedParams\""
        }

        Then("suggested_action이 snake_case로 직렬화된다") {
            val json = objectMapper.writeValueAsString(payload)
            json shouldContain "\"suggested_action\""
            json shouldNotContain "\"suggestedAction\""
        }
    }

    Given("suggestedParams가 null인 McpErrorPayload") {
        val payload = McpErrorPayload(
            code = "NOT_FOUND",
            message = "리소스를 찾을 수 없습니다.",
            recoverable = false,
        )

        Then("suggested_params가 null로 직렬화된다") {
            objectMapper.writeValueAsString(payload)
            payload.suggestedParams shouldBe null
            payload.recoverable shouldBe false
        }
    }
})
