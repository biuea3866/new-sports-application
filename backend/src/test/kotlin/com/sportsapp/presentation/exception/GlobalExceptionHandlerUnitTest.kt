package com.sportsapp.presentation.exception

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.orm.ObjectOptimisticLockingFailureException

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
    }
})
