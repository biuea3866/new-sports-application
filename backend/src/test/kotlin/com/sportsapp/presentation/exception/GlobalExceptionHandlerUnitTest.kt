package com.sportsapp.presentation.exception

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.web.bind.MissingServletRequestParameterException
import java.sql.SQLTransientConnectionException

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

        /**
         * [FIX-03] HikariCP 풀 고갈 시 JpaTransactionManager.doBegin() 이 커넥션 획득 실패를
         * CannotCreateTransactionException 으로 감싼다 — 애플리케이션 버그(500)와 구분해
         * 인프라 포화(503)로 분류하고 Retry-After 를 포함해야 한다 (LoadSheddingFilter 와 동일 계약).
         */
        When("CannotCreateTransactionException(커넥션 풀 고갈) 이 입력되면") {
            val exception = CannotCreateTransactionException(
                "Could not open JPA EntityManager for transaction",
                SQLTransientConnectionException("HikariPool-1 - Connection is not available, request timed out after 5000ms."),
            )
            val response = handler.handleCannotCreateTransactionException(exception)

            Then("HTTP 503 을 반환한다 (500 과 분리)") {
                response.statusCode.value() shouldBe 503
            }
            Then("ProblemDetail 에 code=SERVICE_UNAVAILABLE 이 포함된다") {
                val body = response.body ?: error("response body must not be null")
                val properties = body.properties ?: error("properties must not be null")
                properties["code"] shouldBe "SERVICE_UNAVAILABLE"
            }
            Then("Retry-After 헤더가 포함된다") {
                response.headers.getFirst("Retry-After").shouldNotBeNull()
            }
        }
    }
})
