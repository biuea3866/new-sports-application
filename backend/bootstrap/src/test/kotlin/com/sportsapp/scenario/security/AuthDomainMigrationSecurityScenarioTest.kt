package com.sportsapp.scenario.security

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.user.gateway.JwtIssuer
import com.sportsapp.presentation.support.bearerTokenFor
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

/**
 * AUTH-04 — X-User-Id 헤더 인증을 JWT(`@AuthenticationPrincipal UserPrincipal`)로 전환하며
 * SecurityConfig에 새로 등록한 인가 규칙을 실 필터체인(JwtAuthenticationFilter +
 * authorizeHttpRequests)으로 검증한다. "JWT 없으면 401 / 유효 JWT면 인가 계층은 통과(그 뒤
 * 비즈니스 응답은 각 도메인 책임)"을 도메인별로 확인한다.
 */
class AuthDomainMigrationSecurityScenarioTest(
    @Autowired private val restTemplate: TestRestTemplate,
    @Autowired private val jwtIssuer: JwtIssuer,
) : BaseIntegrationTest() {

    private fun authHeaders(userId: Long): HttpHeaders = HttpHeaders().apply {
        set(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(userId))
        contentType = MediaType.APPLICATION_JSON
    }

    private fun get(path: String, headers: HttpHeaders = HttpHeaders()) =
        restTemplate.exchange(path, HttpMethod.GET, HttpEntity<Unit>(headers), String::class.java)

    private fun post(path: String, headers: HttpHeaders = HttpHeaders(), body: String = "{}") =
        restTemplate.exchange(path, HttpMethod.POST, HttpEntity(body, headers), String::class.java)

    init {
        Given("JWT 없이 개인 데이터 엔드포인트를 호출하면") {
            When("[S-01] GET /bookings/me") {
                Then("401을 반환한다") { get("/bookings/me").statusCode shouldBe HttpStatus.UNAUTHORIZED }
            }
            When("[S-02] GET /payments/me") {
                Then("401을 반환한다") { get("/payments/me").statusCode shouldBe HttpStatus.UNAUTHORIZED }
            }
            When("[S-03] GET /goods-orders/me") {
                Then("401을 반환한다") { get("/goods-orders/me").statusCode shouldBe HttpStatus.UNAUTHORIZED }
            }
            When("[S-04] GET /notifications/me") {
                Then("401을 반환한다") { get("/notifications/me").statusCode shouldBe HttpStatus.UNAUTHORIZED }
            }
            When("[S-05] GET /cart/me") {
                Then("401을 반환한다") { get("/cart/me").statusCode shouldBe HttpStatus.UNAUTHORIZED }
            }
            When("[S-06] GET /operator/inbox") {
                Then("401을 반환한다") { get("/operator/inbox").statusCode shouldBe HttpStatus.UNAUTHORIZED }
            }
            When("[S-07] POST /posts") {
                Then("401을 반환한다") { post("/posts").statusCode shouldBe HttpStatus.UNAUTHORIZED }
            }
            When("[S-08] POST /applications/1/cancel") {
                Then("401을 반환한다") { post("/applications/1/cancel").statusCode shouldBe HttpStatus.UNAUTHORIZED }
            }
        }

        Given("JWT 없이 공개 브라우징 엔드포인트를 호출하면") {
            When("[S-09] GET /posts") {
                Then("401이 아닌 응답(공개 조회)을 반환한다") { get("/posts").statusCode shouldNotBe HttpStatus.UNAUTHORIZED }
            }
            When("[S-10] GET /facilities") {
                Then("401이 아닌 응답(공개 조회)을 반환한다") { get("/facilities").statusCode shouldNotBe HttpStatus.UNAUTHORIZED }
            }
            When("[S-11] GET /events") {
                Then("401이 아닌 응답(공개 조회)을 반환한다") { get("/events").statusCode shouldNotBe HttpStatus.UNAUTHORIZED }
            }
            When("[S-12] GET /limited-drops/1") {
                Then("401이 아닌 응답(공개 조회)을 반환한다") { get("/limited-drops/1").statusCode shouldNotBe HttpStatus.UNAUTHORIZED }
            }
            When("[S-13] GET /recruitments") {
                Then("401이 아닌 응답(공개 조회)을 반환한다") { get("/recruitments").statusCode shouldNotBe HttpStatus.UNAUTHORIZED }
            }
        }

        Given("JWT 없이 결제 웹훅(PG 콜백)을 호출하면") {
            When("[S-14] POST /payments/webhook") {
                Then("401이 아닌 응답(외부 PG 콜백, permitAll)을 반환한다") {
                    post("/payments/webhook").statusCode shouldNotBe HttpStatus.UNAUTHORIZED
                }
            }
        }

        Given("유효한 JWT로 개인 데이터 엔드포인트를 호출하면") {
            When("[S-15] GET /bookings/me") {
                Then("401이 아닌 응답(인가 계층 통과)을 반환한다") {
                    get("/bookings/me", authHeaders(9001L)).statusCode shouldNotBe HttpStatus.UNAUTHORIZED
                }
            }
            When("[S-16] GET /payments/me") {
                Then("401이 아닌 응답(인가 계층 통과)을 반환한다") {
                    get("/payments/me", authHeaders(9001L)).statusCode shouldNotBe HttpStatus.UNAUTHORIZED
                }
            }
            When("[S-17] GET /goods-orders/me") {
                Then("401이 아닌 응답(인가 계층 통과)을 반환한다") {
                    get("/goods-orders/me", authHeaders(9001L)).statusCode shouldNotBe HttpStatus.UNAUTHORIZED
                }
            }
            When("[S-18] GET /notifications/me") {
                Then("401이 아닌 응답(인가 계층 통과)을 반환한다") {
                    get("/notifications/me", authHeaders(9001L)).statusCode shouldNotBe HttpStatus.UNAUTHORIZED
                }
            }
            When("[S-19] GET /cart/me") {
                Then("401이 아닌 응답(인가 계층 통과)을 반환한다") {
                    get("/cart/me", authHeaders(9001L)).statusCode shouldNotBe HttpStatus.UNAUTHORIZED
                }
            }
            When("[S-20] GET /operator/inbox") {
                Then("401이 아닌 응답(인가 계층 통과)을 반환한다") {
                    get("/operator/inbox", authHeaders(9001L)).statusCode shouldNotBe HttpStatus.UNAUTHORIZED
                }
            }
            When("[S-21] GET /applications") {
                Then("401이 아닌 응답(인가 계층 통과)을 반환한다") {
                    get("/applications", authHeaders(9001L)).statusCode shouldNotBe HttpStatus.UNAUTHORIZED
                }
            }
        }
    }
}
