package com.sportsapp.infrastructure.loadshedding

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * [F2] 실측 부하 테스트에서 동시 인플라이트 요청이 CPU 한계를 넘으면 서블릿 큐잉(최대 60초)으로
 * 대기하다 한 번 무너지면 재기동 전까지 영구 열화한다. 허용량을 초과한 요청은 큐잉 대신 즉시
 * 503으로 거부한다(fast-fail).
 */
class LoadSheddingFilterTest : BehaviorSpec({

    Given("허용 동시 처리량이 모두 소진된 상황(permits=0)") {
        val filter = LoadSheddingFilter(maxConcurrentRequests = 0, enabled = true)
        val request = MockHttpServletRequest().apply { requestURI = "/products" }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("즉시 503 ProblemDetail을 반환하고 체인을 진행하지 않는다") {
                response.status shouldBe 503
                response.contentAsString shouldBe
                    """{"status":503,"title":"Service Unavailable",""" +
                        """"detail":"Server is under heavy load. Please retry shortly.",""" +
                        """"properties":{"code":"SERVICE_UNAVAILABLE"}}"""
                verify(exactly = 0) { filterChain.doFilter(any(), any()) }
            }

            Then("[F2] Retry-After 헤더를 포함한다") {
                response.getHeader("Retry-After") shouldBe "1"
            }
        }
    }

    Given("허용 동시 처리량에 여유가 있는 상황(permits=1)") {
        val filter = LoadSheddingFilter(maxConcurrentRequests = 1, enabled = true)
        val request = MockHttpServletRequest().apply { requestURI = "/products" }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("체인이 그대로 진행되고 503으로 거부되지 않는다") {
                verify(exactly = 1) { filterChain.doFilter(request, response) }
                response.status shouldBe 200
            }
        }
    }

    Given("permits=1로 첫 요청이 정상 완료되어 세마포어가 반환된 상황") {
        val filter = LoadSheddingFilter(maxConcurrentRequests = 1, enabled = true)
        val firstRequest = MockHttpServletRequest().apply { requestURI = "/products" }
        val firstResponse = MockHttpServletResponse()
        val firstChain = mockk<FilterChain>(relaxed = true)
        filter.doFilter(firstRequest, firstResponse, firstChain)

        When("다음 요청이 다시 들어오면") {
            val secondRequest = MockHttpServletRequest().apply { requestURI = "/products" }
            val secondResponse = MockHttpServletResponse()
            val secondChain = mockk<FilterChain>(relaxed = true)
            filter.doFilter(secondRequest, secondResponse, secondChain)

            Then("release 후 재획득이 가능해 체인이 다시 정상 진행된다") {
                verify(exactly = 1) { secondChain.doFilter(secondRequest, secondResponse) }
                secondResponse.status shouldBe 200
            }
        }
    }

    Given("permits=0으로 응답 처리 중 예외가 발생해도") {
        val filter = LoadSheddingFilter(maxConcurrentRequests = 1, enabled = true)
        val failingRequest = MockHttpServletRequest().apply { requestURI = "/products" }
        val failingResponse = MockHttpServletResponse()
        val failingChain = mockk<FilterChain>()
        io.mockk.every { failingChain.doFilter(any(), any()) } throws RuntimeException("downstream failure")

        When("filter를 통과하면") {
            Then("세마포어는 반환되어 이후 요청이 정상 처리된다") {
                try {
                    filter.doFilter(failingRequest, failingResponse, failingChain)
                } catch (_: RuntimeException) {
                    // downstream 예외는 그대로 전파되는 것이 정상 — 여기서는 release 여부만 검증
                }

                val nextRequest = MockHttpServletRequest().apply { requestURI = "/products" }
                val nextResponse = MockHttpServletResponse()
                val nextChain = mockk<FilterChain>(relaxed = true)
                filter.doFilter(nextRequest, nextResponse, nextChain)

                verify(exactly = 1) { nextChain.doFilter(nextRequest, nextResponse) }
                nextResponse.status shouldBe 200
            }
        }
    }

    Given("permits=0이지만 헬스체크 경로로 요청하는 상황") {
        val filter = LoadSheddingFilter(maxConcurrentRequests = 0, enabled = true)
        val request = MockHttpServletRequest().apply { requestURI = "/actuator/health" }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("셰딩 대상에서 제외되어 체인이 그대로 진행된다") {
                verify(exactly = 1) { filterChain.doFilter(request, response) }
                response.status shouldBe 200
            }
        }
    }

    Given("[F2] permits=0이지만 load-shedding.enabled=false로 기능이 꺼진 상황") {
        val filter = LoadSheddingFilter(maxConcurrentRequests = 0, enabled = false)
        val request = MockHttpServletRequest().apply { requestURI = "/products" }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("셰딩을 적용하지 않고 체인이 그대로 진행된다") {
                verify(exactly = 1) { filterChain.doFilter(request, response) }
                response.status shouldBe 200
            }
        }
    }
})
