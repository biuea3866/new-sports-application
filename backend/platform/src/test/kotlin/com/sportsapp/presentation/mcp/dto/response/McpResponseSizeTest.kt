package com.sportsapp.presentation.mcp.dto.response

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class McpResponseSizeTest : BehaviorSpec({

    Given("256KB 이하 응답 (정상 케이스)") {
        val serialized = ByteArray(McpResponseSize.MAX_RESPONSE_BYTES)

        Then("shouldReducePageSize가 null을 반환한다") {
            McpResponseSize.shouldReducePageSize(serialized, requestedPageSize = 20).shouldBeNull()
        }
    }

    Given("정확히 256KB 경계 응답") {
        val serialized = ByteArray(McpResponseSize.MAX_RESPONSE_BYTES)

        Then("shouldReducePageSize가 null을 반환한다") {
            McpResponseSize.shouldReducePageSize(serialized, requestedPageSize = 100).shouldBeNull()
        }
    }

    Given("256KB 초과 응답 (300KB, page_size=100)") {
        val actualBytes = 300 * 1024
        val serialized = ByteArray(actualBytes)

        Then("[U-02] suggested_page_size = floor(100 * 262144 / 307200) = 85") {
            val reduced = McpResponseSize.shouldReducePageSize(serialized, requestedPageSize = 100)
            val expected = (100L * McpResponseSize.MAX_RESPONSE_BYTES / actualBytes).toInt()
            reduced shouldBe expected
        }
    }

    Given("1KB 미만 응답") {
        val serialized = ByteArray(512)

        Then("shouldReducePageSize가 null을 반환한다") {
            McpResponseSize.shouldReducePageSize(serialized, requestedPageSize = 20).shouldBeNull()
        }
    }

    Given("매우 큰 응답 (1MB, page_size=100)으로 reduced가 1 이상 보장") {
        val serialized = ByteArray(1024 * 1024)

        Then("shouldReducePageSize가 1 이상 값을 반환한다") {
            val reduced = McpResponseSize.shouldReducePageSize(serialized, requestedPageSize = 100)
            reduced shouldBe (100L * McpResponseSize.MAX_RESPONSE_BYTES / (1024 * 1024)).toInt().coerceAtLeast(1)
        }
    }
})
