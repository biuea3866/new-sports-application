package com.sportsapp.presentation.mcp.response

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class McpPaginationTest : BehaviorSpec({

    Given("total=100, size=20") {
        Then("[R-01] page=0이면 hasNext=true이다") {
            val pagination = McpPagination.of(page = 0, size = 20, total = 100)
            pagination.hasNext shouldBe true
        }

        Then("[R-01] page=4이면 hasNext=false이다 (마지막 페이지)") {
            val pagination = McpPagination.of(page = 4, size = 20, total = 100)
            pagination.hasNext shouldBe false
        }

        Then("[R-01] page=3이면 hasNext=true이다 (마지막 페이지 직전)") {
            val pagination = McpPagination.of(page = 3, size = 20, total = 100)
            pagination.hasNext shouldBe true
        }
    }

    Given("total=0") {
        Then("hasNext=false이다") {
            val pagination = McpPagination.of(page = 0, size = 20, total = 0)
            pagination.hasNext shouldBe false
        }
    }

    Given("total=1, size=20") {
        Then("page=0이면 hasNext=false이다 (단건)") {
            val pagination = McpPagination.of(page = 0, size = 20, total = 1)
            pagination.hasNext shouldBe false
        }
    }

    Given("total=21, size=20") {
        Then("page=0이면 hasNext=true이다 (2페이지 존재)") {
            val pagination = McpPagination.of(page = 0, size = 20, total = 21)
            pagination.hasNext shouldBe true
        }

        Then("page=1이면 hasNext=false이다 (마지막 페이지)") {
            val pagination = McpPagination.of(page = 1, size = 20, total = 21)
            pagination.hasNext shouldBe false
        }
    }
})
