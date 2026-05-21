package com.sportsapp.scenario.goods

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.user.LoginResponse
import com.sportsapp.domain.user.UserDomainService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

class B2bProductApiScenarioTest(
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbcTemplate: JdbcTemplate,
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

    private fun login(email: String, password: String): String {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val response = restTemplate.exchange(
            "${baseUrl()}/auth/login",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java,
        )
        check(response.statusCode == HttpStatus.OK) { "Login failed for $email: ${response.statusCode}" }
        return objectMapper.readValue(response.body, LoginResponse::class.java).accessToken
    }

    private fun jsonHeaders(token: String): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }

    init {
        beforeSpec {
            jdbcTemplate.execute("DELETE FROM stocks")
            jdbcTemplate.execute("DELETE FROM products")
        }

        Given("[S-01] GOODS_SELLER 사용자가 Product 등록 → restoreStock → activate → 목록 조회 전체 흐름") {
            val password = "Seller12345!"
            val seller = userDomainService.register("b2b-product-seller-s01@example.com", password)
            userDomainService.assignRole(adminId = seller.id, userId = seller.id, roleName = "GOODS_SELLER")
            val token = login("b2b-product-seller-s01@example.com", password)

            val createBody = objectMapper.writeValueAsString(
                mapOf(
                    "name" to "프리미엄 테니스 라켓",
                    "category" to "EQUIPMENT",
                    "price" to 50000,
                    "description" to "최고급 카본 프레임",
                    "imageUrl" to "https://example.com/racket.jpg",
                )
            )

            When("POST /api/b2b/products 로 상품 등록하면") {
                val createResponse = restTemplate.exchange(
                    "${baseUrl()}/api/b2b/products",
                    HttpMethod.POST,
                    HttpEntity(createBody, jsonHeaders(token)),
                    String::class.java,
                )

                Then("201 Created와 초기 stock=0이 반환된다") {
                    createResponse.statusCode shouldBe HttpStatus.CREATED
                    val tree = objectMapper.readTree(createResponse.body)
                    tree.get("status").asText() shouldBe "INACTIVE"
                    tree.get("stockQuantity").asInt() shouldBe 0
                    val productId = tree.get("id").asLong()

                    val restoreBody = objectMapper.writeValueAsString(mapOf("quantity" to 10))
                    val restoreResponse = restTemplate.exchange(
                        "${baseUrl()}/api/b2b/products/$productId/stock/restore",
                        HttpMethod.POST,
                        HttpEntity(restoreBody, jsonHeaders(token)),
                        String::class.java,
                    )
                    restoreResponse.statusCode shouldBe HttpStatus.OK
                    objectMapper.readTree(restoreResponse.body).get("stockQuantity").asInt() shouldBe 10

                    val activateResponse = restTemplate.exchange(
                        "${baseUrl()}/api/b2b/products/$productId/activate",
                        HttpMethod.POST,
                        HttpEntity<Void>(jsonHeaders(token)),
                        String::class.java,
                    )
                    activateResponse.statusCode shouldBe HttpStatus.OK
                    objectMapper.readTree(activateResponse.body).get("status").asText() shouldBe "ACTIVE"

                    val listResponse = restTemplate.exchange(
                        "${baseUrl()}/api/b2b/products",
                        HttpMethod.GET,
                        HttpEntity<Void>(jsonHeaders(token)),
                        String::class.java,
                    )
                    listResponse.statusCode shouldBe HttpStatus.OK
                    val content = objectMapper.readTree(listResponse.body).get("content")
                    content shouldNotBe null
                }
            }
        }

        Given("[S-02] 다른 사용자가 Product를 PATCH 시도하면") {
            val ownerPassword = "Owner12345!"
            val owner = userDomainService.register("b2b-product-owner-s02@example.com", ownerPassword)
            userDomainService.assignRole(adminId = owner.id, userId = owner.id, roleName = "GOODS_SELLER")
            val ownerToken = login("b2b-product-owner-s02@example.com", ownerPassword)

            val otherPassword = "Other12345!"
            val other = userDomainService.register("b2b-product-other-s02@example.com", otherPassword)
            userDomainService.assignRole(adminId = other.id, userId = other.id, roleName = "GOODS_SELLER")
            val otherToken = login("b2b-product-other-s02@example.com", otherPassword)

            val createBody = objectMapper.writeValueAsString(
                mapOf(
                    "name" to "소유자 상품",
                    "category" to "EQUIPMENT",
                    "price" to 30000,
                    "description" to "소유자만 수정 가능",
                    "imageUrl" to "https://example.com/owner.jpg",
                )
            )
            val createResponse = restTemplate.exchange(
                "${baseUrl()}/api/b2b/products",
                HttpMethod.POST,
                HttpEntity(createBody, jsonHeaders(ownerToken)),
                String::class.java,
            )
            val productId = objectMapper.readTree(createResponse.body).get("id").asLong()

            When("다른 사업자가 PATCH 시도하면") {
                val patchBody = objectMapper.writeValueAsString(mapOf("name" to "해킹 시도"))
                val patchResponse = restTemplate.exchange(
                    "${baseUrl()}/api/b2b/products/$productId",
                    HttpMethod.PATCH,
                    HttpEntity(patchBody, jsonHeaders(otherToken)),
                    String::class.java,
                )

                Then("404 응답이 반환된다") {
                    patchResponse.statusCode shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        Given("[S-03] GOODS_SELLER Role 없는 사용자가 POST 시도하면") {
            val password = "NoRole12345!"
            userDomainService.register("b2b-product-norole-s03@example.com", password)
            val token = login("b2b-product-norole-s03@example.com", password)

            When("POST /api/b2b/products 호출하면") {
                val createBody = objectMapper.writeValueAsString(
                    mapOf(
                        "name" to "무권한 상품",
                        "category" to "EQUIPMENT",
                        "price" to 10000,
                        "description" to "권한 없음",
                        "imageUrl" to "https://example.com/norole.jpg",
                    )
                )
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/b2b/products",
                    HttpMethod.POST,
                    HttpEntity(createBody, jsonHeaders(token)),
                    String::class.java,
                )

                Then("403 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }

        Given("[S-04] 재고 음수 입력 시") {
            val password = "Seller12345!"
            val seller = userDomainService.register("b2b-product-seller-s04@example.com", password)
            userDomainService.assignRole(adminId = seller.id, userId = seller.id, roleName = "GOODS_SELLER")
            val token = login("b2b-product-seller-s04@example.com", password)

            val createBody = objectMapper.writeValueAsString(
                mapOf(
                    "name" to "재고 테스트 상품",
                    "category" to "EQUIPMENT",
                    "price" to 10000,
                    "description" to "재고 음수 검증",
                    "imageUrl" to "https://example.com/stock.jpg",
                )
            )
            val createResponse = restTemplate.exchange(
                "${baseUrl()}/api/b2b/products",
                HttpMethod.POST,
                HttpEntity(createBody, jsonHeaders(token)),
                String::class.java,
            )
            val productId = objectMapper.readTree(createResponse.body).get("id").asLong()

            When("quantity=-1로 restoreStock 호출하면") {
                val restoreBody = objectMapper.writeValueAsString(mapOf("quantity" to -1))
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/b2b/products/$productId/stock/restore",
                    HttpMethod.POST,
                    HttpEntity(restoreBody, jsonHeaders(token)),
                    String::class.java,
                )

                Then("400 Bad Request가 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNPROCESSABLE_ENTITY
                }
            }
        }

        Given("[S-미인증] 미인증 사용자가 /api/b2b/products에 접근하면") {
            When("GET /api/b2b/products 를 인증 없이 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/b2b/products",
                    HttpMethod.GET,
                    HttpEntity<Void>(HttpHeaders()),
                    String::class.java,
                )

                Then("401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }
    }
}
