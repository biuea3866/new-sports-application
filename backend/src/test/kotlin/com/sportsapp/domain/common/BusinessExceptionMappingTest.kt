package com.sportsapp.domain.common

import com.sportsapp.domain.common.exceptions.BusinessRuleViolationException
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 도메인 예외별 HTTP 상태 코드 매핑이 정의된 표대로 동작한다.
 */
class BusinessExceptionMappingTest : BehaviorSpec({

    Given("ResourceNotFoundException") {
        val exception = ResourceNotFoundException("Seat", 42L)
        When("status를 조회하면") {
            Then("NOT_FOUND(404) 로 매핑된다") {
                exception.status shouldBe ErrorStatus.NOT_FOUND
                exception.status.httpStatus shouldBe 404
            }
        }
        When("errorCode를 조회하면") {
            Then("RESOURCE_NOT_FOUND 코드를 반환한다") {
                exception.errorCode shouldBe "RESOURCE_NOT_FOUND"
            }
        }
    }

    Given("BusinessRuleViolationException") {
        val exception = BusinessRuleViolationException("seat 42 is already locked")
        When("status를 조회하면") {
            Then("UNPROCESSABLE(422) 로 매핑된다") {
                exception.status shouldBe ErrorStatus.UNPROCESSABLE
                exception.status.httpStatus shouldBe 422
            }
        }
        When("errorCode를 조회하면") {
            Then("BUSINESS_RULE_VIOLATION 코드를 반환한다") {
                exception.errorCode shouldBe "BUSINESS_RULE_VIOLATION"
            }
        }
    }

    Given("ErrorStatus enum") {
        When("각 상태값의 httpStatus를 조회하면") {
            Then("표대로 매핑된다") {
                ErrorStatus.NOT_FOUND.httpStatus shouldBe 404
                ErrorStatus.CONFLICT.httpStatus shouldBe 409
                ErrorStatus.UNPROCESSABLE.httpStatus shouldBe 422
                ErrorStatus.BAD_REQUEST.httpStatus shouldBe 400
                ErrorStatus.UNAUTHORIZED.httpStatus shouldBe 401
                ErrorStatus.FORBIDDEN.httpStatus shouldBe 403
                ErrorStatus.INTERNAL.httpStatus shouldBe 500
            }
        }
        When("TOO_EARLY의 httpStatus를 조회하면") {
            Then("425로 매핑된다") {
                ErrorStatus.TOO_EARLY.httpStatus shouldBe 425
            }
        }
    }
})
