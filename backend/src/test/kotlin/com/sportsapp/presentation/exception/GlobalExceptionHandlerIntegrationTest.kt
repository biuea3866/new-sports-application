package com.sportsapp.presentation.exception

import com.sportsapp.domain.common.exceptions.BusinessRuleViolationException
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container

/**
 * S-01: 의도적 BusinessException 발생 시 Controller가 정확한 ProblemDetail + 매핑된 HTTP 상태를 반환한다.
 * S-02: RuntimeException은 500 + INTERNAL_ERROR 코드의 ProblemDetail로 변환된다.
 * S-03: @Valid 실패 시 422 + 필드별 에러 목록 포함 ProblemDetail이 반환된다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(ExceptionTriggerController::class)
class GlobalExceptionHandlerIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    companion object {
        @Container
        @ServiceConnection
        val mysqlContainer: MySQLContainer<*> = MySQLContainer("mysql:8.0")
            .withDatabaseName("sports")
            .withUsername("test")
            .withPassword("test")
            .also { it.start() }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        Given("ResourceNotFoundException이 발생하는 엔드포인트") {
            When("GET /test/exceptions/resource-not-found 요청 시") {
                Then("[S-01] 404 + ProblemDetail (code=RESOURCE_NOT_FOUND) 을 반환한다") {
                    mockMvc.get("/test/exceptions/resource-not-found") {
                        accept(MediaType.APPLICATION_JSON)
                    }.andExpect {
                        status { isNotFound() }
                        content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                        jsonPath("$.status") { value(404) }
                        jsonPath("$.properties.code") { value("RESOURCE_NOT_FOUND") }
                        jsonPath("$.detail") { exists() }
                        jsonPath("$.type") { exists() }
                    }
                }
            }
        }

        Given("BusinessRuleViolationException이 발생하는 엔드포인트") {
            When("GET /test/exceptions/business-rule-violation 요청 시") {
                Then("[S-01] 422 + ProblemDetail (code=BUSINESS_RULE_VIOLATION) 을 반환한다") {
                    mockMvc.get("/test/exceptions/business-rule-violation") {
                        accept(MediaType.APPLICATION_JSON)
                    }.andExpect {
                        status { isUnprocessableEntity() }
                        content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                        jsonPath("$.status") { value(422) }
                        jsonPath("$.properties.code") { value("BUSINESS_RULE_VIOLATION") }
                    }
                }
            }
        }

        Given("알 수 없는 RuntimeException이 발생하는 엔드포인트") {
            When("GET /test/exceptions/unknown-error 요청 시") {
                Then("[S-02] 500 + ProblemDetail (code=INTERNAL_ERROR) 을 반환한다") {
                    mockMvc.get("/test/exceptions/unknown-error") {
                        accept(MediaType.APPLICATION_JSON)
                    }.andExpect {
                        status { isInternalServerError() }
                        content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                        jsonPath("$.status") { value(500) }
                        jsonPath("$.properties.code") { value("INTERNAL_ERROR") }
                    }
                }
            }
        }

        Given("@Valid 검증이 실패하는 요청") {
            When("POST /test/exceptions/validation 에 빈 name 으로 요청 시") {
                Then("[S-03] 422 + fieldErrors 목록이 포함된 ProblemDetail 을 반환한다") {
                    mockMvc.post("/test/exceptions/validation") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"name": ""}"""
                        accept(MediaType.APPLICATION_JSON)
                    }.andExpect {
                        status { isUnprocessableEntity() }
                        content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                        jsonPath("$.status") { value(422) }
                        jsonPath("$.properties.fieldErrors") { exists() }
                        jsonPath("$.properties.fieldErrors") { isArray() }
                    }
                }
            }
        }
    }
}

@RestController
@RequestMapping("/test/exceptions")
class ExceptionTriggerController {

    @GetMapping("/resource-not-found")
    fun throwResourceNotFound(): String {
        throw ResourceNotFoundException("Seat", 42L)
    }

    @GetMapping("/business-rule-violation")
    fun throwBusinessRuleViolation(): String {
        throw BusinessRuleViolationException("seat 42 is locked by another user")
    }

    @GetMapping("/unknown-error")
    fun throwUnknownError(): String {
        error("unexpected system error")
    }

    @PostMapping("/validation")
    fun validateRequest(@Valid @RequestBody request: ValidatableRequest): String {
        return request.name
    }
}

data class ValidatableRequest(@field:NotBlank val name: String)
