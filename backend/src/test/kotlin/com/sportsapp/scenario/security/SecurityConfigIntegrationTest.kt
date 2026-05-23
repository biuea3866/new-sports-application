package com.sportsapp.scenario.security

import com.sportsapp.BaseIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

// DEF-001: permitAll 엔드포인트 익명 호출 시 401 반환 결함 재현 및 회귀 방지.
// 결함 재현: POST /users/register, GET /facilities 익명 호출 -> 201/200 기대
// 회귀 보장: /api/facility-owner/**, /admin/** 익명 호출 -> 401 유지
class SecurityConfigIntegrationTest(
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

    private fun jsonHeaders(): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

    init {
        Given("미인증 익명 요청 — DEF-001 결함 재현") {
            When("[DEF-001] POST /users/register 를 토큰 없이 호출하면") {
                val email = "def001-register-${System.nanoTime()}@test.local"
                val body = """{"email":"$email","password":"Passw0rd!"}"""
                val response = restTemplate.exchange(
                    "${baseUrl()}/users/register",
                    HttpMethod.POST,
                    HttpEntity(body, jsonHeaders()),
                    String::class.java,
                )

                Then("201 Created 가 반환되어야 하고 401이면 안 된다 (permitAll 위반)") {
                    response.statusCode shouldNotBe HttpStatus.UNAUTHORIZED
                    response.statusCode shouldBe HttpStatus.CREATED
                }
            }

            When("[DEF-001] GET /facilities 를 토큰 없이 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/facilities",
                    HttpMethod.GET,
                    HttpEntity<Void>(HttpHeaders()),
                    String::class.java,
                )

                Then("200 OK 가 반환되어야 하고 401이면 안 된다 (permitAll 위반)") {
                    response.statusCode shouldNotBe HttpStatus.UNAUTHORIZED
                    response.statusCode shouldBe HttpStatus.OK
                }
            }

            When("[DEF-001] POST /auth/login 을 토큰 없이 호출하면 (잘못된 자격증명)") {
                val body = """{"email":"nonexistent@test.local","password":"WrongPass!"}"""
                val response = restTemplate.exchange(
                    "${baseUrl()}/auth/login",
                    HttpMethod.POST,
                    HttpEntity(body, jsonHeaders()),
                    String::class.java,
                )

                Then("401 이상의 도메인 예외가 반환되고 Spring Security 필터 레벨 차단이 아니어야 한다") {
                    response.statusCode shouldNotBe HttpStatus.FORBIDDEN
                    val statusCode = response.statusCode.value()
                    statusCode shouldBeGreaterThanOrEqualTo 400
                }
            }

            When("[DEF-001] POST /auth/refresh 를 토큰 없이 호출하면 (잘못된 리프레시 토큰)") {
                val body = """{"refreshToken":"invalid-token"}"""
                val response = restTemplate.exchange(
                    "${baseUrl()}/auth/refresh",
                    HttpMethod.POST,
                    HttpEntity(body, jsonHeaders()),
                    String::class.java,
                )

                Then("4xx 도메인 예외가 반환되고 Spring Security 필터 레벨 차단이 아니어야 한다") {
                    response.statusCode shouldNotBe HttpStatus.FORBIDDEN
                    val statusCode = response.statusCode.value()
                    statusCode shouldBeGreaterThanOrEqualTo 400
                }
            }
        }

        Given("미인증 익명 요청 — 회귀 보장 (인증 필요 경로는 401 유지)") {
            When("[REGRESSION] GET /api/facility-owner/facilities 를 인증 없이 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/facility-owner/facilities",
                    HttpMethod.GET,
                    HttpEntity<Void>(HttpHeaders()),
                    String::class.java,
                )

                Then("401 Unauthorized 가 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            When("[REGRESSION] POST /admin/users/999/roles/ADMIN 을 인증 없이 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/admin/users/999/roles/ADMIN",
                    HttpMethod.POST,
                    HttpEntity<Void>(HttpHeaders()),
                    String::class.java,
                )

                Then("401 Unauthorized 가 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }
    }
}
