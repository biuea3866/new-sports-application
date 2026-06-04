package com.sportsapp.presentation.mcp.dto.response

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class McpResponseTest : BehaviorSpec({

    val objectMapper = ObjectMapper().registerKotlinModule()

    Given("정상 데이터와 페이지네이션") {
        val pagination = McpPagination.of(page = 0, size = 20, total = 100)
        val response = McpResponse.ok(data = mapOf("id" to 1L), pagination = pagination)

        Then("[U-01] status=OK, data, pagination 필드를 포함해 wrap된다") {
            response.status shouldBe McpResponseStatus.OK
            response.data shouldBe mapOf("id" to 1L)
            response.pagination shouldBe pagination
            response.error.shouldBeNull()
        }

        Then("[U-01] JSON 직렬화 시 snake_case 키를 사용한다") {
            val json = objectMapper.writeValueAsString(response)
            json shouldContain "\"has_next\""
            json shouldNotContain "\"hasNext\""
        }
    }

    Given("에러 페이로드") {
        val payload = McpErrorPayload(
            code = "RESPONSE_TOO_LARGE",
            message = "응답 크기가 256KB를 초과합니다.",
            recoverable = true,
            suggestedAction = "page_size를 줄여서 재호출하세요.",
            suggestedParams = mapOf("page_size" to 85),
        )
        val response = McpResponse.error(payload)

        Then("[U-01] status=ERROR, error 필드를 포함해 wrap된다") {
            response.status shouldBe McpResponseStatus.ERROR
            response.error shouldBe payload
            response.data.shouldBeNull()
            response.pagination.shouldBeNull()
        }

        Then("[U-01] JSON 직렬화 시 error 내부 snake_case 키를 사용한다") {
            val json = objectMapper.writeValueAsString(response)
            json shouldContain "\"suggested_action\""
            json shouldContain "\"suggested_params\""
            json shouldNotContain "\"suggestedAction\""
            json shouldNotContain "\"suggestedParams\""
        }
    }

    Given("confirmRequired 데이터") {
        val data = mapOf<String, Any>("confirm_message" to "정말 삭제하시겠습니까?")
        val response = McpResponse.confirmRequired(data)

        Then("[U-01] status=CONFIRM_REQUIRED, data 필드를 포함해 wrap된다") {
            response.status shouldBe McpResponseStatus.CONFIRM_REQUIRED
            response.data shouldBe data
            response.error.shouldBeNull()
            response.pagination.shouldBeNull()
        }
    }

    Given("단건 조회 응답 (pagination 없음)") {
        val response = McpResponse.ok(data = mapOf("id" to 1L))

        Then("[U-03] pagination이 null로 반환된다") {
            response.pagination.shouldBeNull()
        }
    }
})
