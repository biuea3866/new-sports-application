package com.sportsapp.presentation.exception

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.MissingServletRequestParameterException

class GlobalExceptionHandlerUnitTest : BehaviorSpec({

    val handler = GlobalExceptionHandler()

    Given("GlobalExceptionHandler") {
        When("ObjectOptimisticLockingFailureException 이 입력되면") {
            val exception = ObjectOptimisticLockingFailureException("Stock", 1L)
            val response = handler.handleOptimisticLockException(exception)

            Then("HTTP 409 를 반환한다") {
                response.statusCode.value() shouldBe 409
            }
            Then("ProblemDetail 에 code=OPTIMISTIC_LOCK_CONFLICT 가 포함된다") {
                val body = response.body ?: error("response body must not be null")
                val properties = body.properties ?: error("properties must not be null")
                properties["code"] shouldBe "OPTIMISTIC_LOCK_CONFLICT"
            }
        }

        /**
         * [F6] 필수 @RequestParam 누락 시 던져지는 MissingServletRequestParameterException은
         * 이 핸들러 도입 전에는 매칭되는 @ExceptionHandler가 없어 generic Exception 핸들러(500)로
         * 떨어진다 — /products/popular 를 category 없이 호출하면 500이 재현되는 원인.
         */
        When("MissingServletRequestParameterException 이 입력되면") {
            val exception = MissingServletRequestParameterException("category", "ProductCategory")
            val response = handler.handleMissingServletRequestParameterException(exception)

            Then("HTTP 400 을 반환한다") {
                response.statusCode.value() shouldBe 400
            }
            Then("ProblemDetail 에 code=MISSING_REQUEST_PARAMETER 와 파라미터명이 포함된다") {
                val body = response.body ?: error("response body must not be null")
                val properties = body.properties ?: error("properties must not be null")
                properties["code"] shouldBe "MISSING_REQUEST_PARAMETER"
                body.detail shouldBe "Required request parameter is missing: category"
            }
        }
    }
})
