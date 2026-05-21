package com.sportsapp.scenario.b2b

import com.sportsapp.BaseIntegrationTest
import io.kotest.matchers.shouldBe
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

// [S-03] 익명 사용자가 모든 /api/{facility-owner,event-host,goods-seller,operator}/** GET, POST 호출 시 401
// B2B-02의 라우팅 검증 - 인증 없이 접근 시 일관된 401 반환.
class AnonymousB2bAccessScenarioTest(
    @LocalServerPort private val port: Int,
) : BaseIntegrationTest() {

    private val restTemplate = RestTemplate(
        HttpComponentsClientHttpRequestFactory(HttpClients.createDefault()),
    ).apply {
        errorHandler = object : ResponseErrorHandler {
            override fun hasError(response: ClientHttpResponse): Boolean = false
            override fun handleError(response: ClientHttpResponse) = Unit
        }
    }

    private fun baseUrl() = "http://localhost:$port"

    private val noAuthHeaders = HttpHeaders()

    init {
        Given("[S-03] 인증되지 않은 익명 사용자가") {
            When("[S-03] GET /api/facility-owner/facilities 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/facility-owner/facilities",
                    HttpMethod.GET,
                    HttpEntity<Void>(noAuthHeaders),
                    String::class.java,
                )

                Then("[S-03] 401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            When("[S-03] POST /api/facility-owner/facilities 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/facility-owner/facilities",
                    HttpMethod.POST,
                    HttpEntity<Void>(noAuthHeaders),
                    String::class.java,
                )

                Then("[S-03] 401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            When("[S-03] GET /api/event-host/events 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events",
                    HttpMethod.GET,
                    HttpEntity<Void>(noAuthHeaders),
                    String::class.java,
                )

                Then("[S-03] 401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            When("[S-03] POST /api/event-host/events 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events",
                    HttpMethod.POST,
                    HttpEntity<Void>(noAuthHeaders),
                    String::class.java,
                )

                Then("[S-03] 401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            When("[S-03] GET /api/goods-seller/products 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/goods-seller/products",
                    HttpMethod.GET,
                    HttpEntity<Void>(noAuthHeaders),
                    String::class.java,
                )

                Then("[S-03] 401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            When("[S-03] POST /api/goods-seller/products 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/goods-seller/products",
                    HttpMethod.POST,
                    HttpEntity<Void>(noAuthHeaders),
                    String::class.java,
                )

                Then("[S-03] 401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            When("[S-03] GET /api/operator/dashboard/summary 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/operator/dashboard/summary",
                    HttpMethod.GET,
                    HttpEntity<Void>(noAuthHeaders),
                    String::class.java,
                )

                Then("[S-03] 401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }
    }
}
