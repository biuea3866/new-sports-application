package com.sportsapp.presentation.mcp.audit

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class ToolParamsMaskerTest : BehaviorSpec({

    val objectMapper = ObjectMapper()

    Given("[U-06] namedParams가 비어 있으면") {
        When("mask를 호출하면") {
            val result = ToolParamsMasker.mask(emptyMap(), objectMapper)
            Then("[U-06] 빈 JSON 객체 {} 를 반환한다") {
                result shouldBe "{}"
            }
        }
    }

    Given("[U-07] PII 키(userName)를 포함한 String 값이 있으면") {
        val params = mapOf("userName" to "홍길동", "page" to 0)
        When("mask를 호출하면") {
            val result = ToolParamsMasker.mask(params, objectMapper)
            Then("[U-07] 원본 이름이 JSON에 평문으로 남지 않는다") {
                result shouldNotContain "홍길동"
                result shouldContain "userName"
            }
        }
    }

    Given("[U-08] 숫자 타입(Long) 파라미터는 PII 패턴에 해당하지 않으면") {
        val params = mapOf("userId" to 12345L, "status" to "PENDING")
        When("mask를 호출하면") {
            val result = ToolParamsMasker.mask(params, objectMapper)
            Then("[U-08] 숫자 값은 그대로 JSON에 포함된다") {
                result shouldContain "12345"
                result shouldContain "PENDING"
            }
        }
    }

    Given("[U-09] null 파라미터가 있으면") {
        val params = mapOf("status" to null, "userId" to 100L)
        When("mask를 호출하면") {
            val result = ToolParamsMasker.mask(params, objectMapper)
            Then("[U-09] null은 그대로 null로 직렬화된다") {
                result shouldContain "null"
                result shouldContain "100"
            }
        }
    }

    Given("[U-10] 이메일 키(userEmail)를 포함한 String 값이 있으면") {
        val params = mapOf("userEmail" to "test@example.com")
        When("mask를 호출하면") {
            val result = ToolParamsMasker.mask(params, objectMapper)
            Then("[U-10] 이메일 원본이 평문으로 남지 않는다") {
                result shouldNotContain "test@example.com"
                result shouldContain "userEmail"
            }
        }
    }
})
