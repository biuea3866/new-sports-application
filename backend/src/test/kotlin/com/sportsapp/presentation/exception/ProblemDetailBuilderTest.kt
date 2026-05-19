package com.sportsapp.presentation.exception

import com.sportsapp.domain.common.ErrorStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * [U-02] 응답 빌더가 type/title/status/code/detail 5개 필드를 항상 채운다.
 */
class ProblemDetailBuilderTest : BehaviorSpec({

    Given("ProblemDetailBuilder") {
        When("NOT_FOUND 상태로 빌드하면") {
            val problemDetail = ProblemDetailBuilder.build(
                status = ErrorStatus.NOT_FOUND,
                code = "RESOURCE_NOT_FOUND",
                detail = "Seat with id 42 not found"
            )
            Then("[U-02] type 필드가 채워진다") {
                problemDetail.type shouldNotBe null
                problemDetail.type.toString() shouldBe "https://errors.sports-application/resource-not-found"
            }
            Then("[U-02] title 필드가 채워진다") {
                problemDetail.title shouldBe "Resource Not Found"
            }
            Then("[U-02] status 필드가 채워진다") {
                problemDetail.status shouldBe 404
            }
            Then("[U-02] detail 필드가 채워진다") {
                problemDetail.detail shouldBe "Seat with id 42 not found"
            }
            Then("[U-02] code 필드가 properties에 포함된다") {
                val props = problemDetail.properties ?: error("properties must not be null")
                props["code"] shouldBe "RESOURCE_NOT_FOUND"
            }
        }

        When("CONFLICT 상태로 빌드하면") {
            val problemDetail = ProblemDetailBuilder.build(
                status = ErrorStatus.CONFLICT,
                code = "SEAT_ALREADY_LOCKED",
                detail = "seat 42 is locked by another user"
            )
            Then("[U-02] status 가 409로 설정된다") {
                problemDetail.status shouldBe 409
            }
        }

        When("UNPROCESSABLE 상태로 빌드하면") {
            val problemDetail = ProblemDetailBuilder.build(
                status = ErrorStatus.UNPROCESSABLE,
                code = "BUSINESS_RULE_VIOLATION",
                detail = "business rule violated"
            )
            Then("[U-02] status 가 422로 설정된다") {
                problemDetail.status shouldBe 422
            }
        }
    }
})
