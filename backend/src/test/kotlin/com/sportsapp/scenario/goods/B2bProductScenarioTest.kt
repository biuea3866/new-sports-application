package com.sportsapp.scenario.goods

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.user.LoginResponse
import com.sportsapp.domain.user.UserDomainService
import com.sportsapp.infrastructure.persistence.goods.ProductJpaRepository
import com.sportsapp.infrastructure.persistence.goods.StockJpaRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

class B2bProductScenarioTest(
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val objectMapper: ObjectMapper,
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
        val headers = HttpHeaders().apply { set("Content-Type", "application/json") }
        val body = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val response = restTemplate.exchange(
            "${baseUrl()}/auth/login",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java,
        )
        check(response.statusCode == HttpStatus.OK) { "Login failed: ${response.body}" }
        return objectMapper.readValue(response.body, LoginResponse::class.java).accessToken
    }

    private fun authHeaders(token: String): HttpHeaders = HttpHeaders().apply {
        setBearerAuth(token)
        set("Content-Type", "application/json")
    }

    private fun createProductBody(suffix: String = ""): String = objectMapper.writeValueAsString(
        mapOf(
            "name" to "테니스 라켓$suffix",
            "category" to "EQUIPMENT",
            "price" to 50000,
            "description" to "고급 테니스 라켓",
            "imageUrl" to "https://example.com/racket.jpg",
        ),
    )

    private fun extractId(locationHeader: String): Long =
        locationHeader.substringAfterLast("/").toLong()

    init {
        Given("[S-01] GOODS_SELLER 사용자가 Product 등록 → restoreStock → activate → 목록 조회 전체 흐름") {
            val sellerEmail = "b2b-seller-s01@example.com"
            val sellerPassword = "SellerTest123!"
            val seller = userDomainService.register(sellerEmail, sellerPassword)
            userDomainService.assignRole(seller.id, seller.id, "GOODS_SELLER")
            val token = login(sellerEmail, sellerPassword)
            val headers = authHeaders(token)

            When("POST /api/b2b/products로 상품 등록하면") {
                val createResponse = restTemplate.exchange(
                    "${baseUrl()}/api/b2b/products",
                    HttpMethod.POST,
                    HttpEntity(createProductBody("-S01"), headers),
                    String::class.java,
                )

                Then("[S-01] 201 응답과 Location 헤더가 반환되고 초기 stock=0이다") {
                    createResponse.statusCode shouldBe HttpStatus.CREATED
                    val location = requireNotNull(createResponse.headers.location?.toString())
                    location shouldContain "/api/b2b/products/"
                    val productId = extractId(location)

                    val stock = stockJpaRepository.findByProductId(productId)
                    requireNotNull(stock)
                    stock.quantity shouldBe 0

                    val restoreBody = objectMapper.writeValueAsString(mapOf("quantity" to 10))
                    val restoreResponse = restTemplate.exchange(
                        "${baseUrl()}/api/b2b/products/$productId/stock/restore",
                        HttpMethod.POST,
                        HttpEntity(restoreBody, headers),
                        String::class.java,
                    )
                    restoreResponse.statusCode shouldBe HttpStatus.OK

                    val stockAfterRestore = stockJpaRepository.findByProductId(productId)
                    requireNotNull(stockAfterRestore)
                    stockAfterRestore.quantity shouldBe 10

                    val activateResponse = restTemplate.exchange(
                        "${baseUrl()}/api/b2b/products/$productId/activate",
                        HttpMethod.POST,
                        HttpEntity<Void>(headers),
                        String::class.java,
                    )
                    activateResponse.statusCode shouldBe HttpStatus.OK
                    activateResponse.body shouldContain "ACTIVE"

                    val listResponse = restTemplate.exchange(
                        "${baseUrl()}/api/b2b/products",
                        HttpMethod.GET,
                        HttpEntity<Void>(headers),
                        String::class.java,
                    )
                    listResponse.statusCode shouldBe HttpStatus.OK
                    listResponse.body shouldContain "테니스 라켓-S01"
                    listResponse.body shouldContain "10"
                }
            }
        }

        Given("[S-02] 다른 사용자가 동일 Product PATCH 시 404") {
            val seller1Email = "b2b-seller-s02-a@example.com"
            val seller2Email = "b2b-seller-s02-b@example.com"
            val password = "SellerTest123!"
            val seller1 = userDomainService.register(seller1Email, password)
            userDomainService.assignRole(seller1.id, seller1.id, "GOODS_SELLER")
            val seller2 = userDomainService.register(seller2Email, password)
            userDomainService.assignRole(seller2.id, seller2.id, "GOODS_SELLER")
            val token1 = login(seller1Email, password)
            val token2 = login(seller2Email, password)
            val headers1 = authHeaders(token1)
            val headers2 = authHeaders(token2)

            val createResponse = restTemplate.exchange(
                "${baseUrl()}/api/b2b/products",
                HttpMethod.POST,
                HttpEntity(createProductBody("-S02"), headers1),
                String::class.java,
            )
            val productId = extractId(requireNotNull(createResponse.headers.location?.toString()))

            When("seller2가 seller1의 Product를 PATCH하면") {
                val patchBody = objectMapper.writeValueAsString(mapOf("name" to "탈취 시도"))
                val patchResponse = restTemplate.exchange(
                    "${baseUrl()}/api/b2b/products/$productId",
                    HttpMethod.PATCH,
                    HttpEntity(patchBody, headers2),
                    String::class.java,
                )

                Then("[S-02] 404 응답이 반환된다") {
                    patchResponse.statusCode shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        Given("[S-03] GOODS_SELLER Role 미보유 사용자가 POST 호출 시 403") {
            val userEmail = "b2b-user-s03@example.com"
            val userPassword = "UserTest123!"
            userDomainService.register(userEmail, userPassword)
            val token = login(userEmail, userPassword)
            val headers = authHeaders(token)

            When("GOODS_SELLER Role 없이 POST /api/b2b/products 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/b2b/products",
                    HttpMethod.POST,
                    HttpEntity(createProductBody("-S03"), headers),
                    String::class.java,
                )

                Then("[S-03] 403 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }

        Given("[S-04] 재고 음수 입력 시 400") {
            val sellerEmail = "b2b-seller-s04@example.com"
            val sellerPassword = "SellerTest123!"
            val seller = userDomainService.register(sellerEmail, sellerPassword)
            userDomainService.assignRole(seller.id, seller.id, "GOODS_SELLER")
            val token = login(sellerEmail, sellerPassword)
            val headers = authHeaders(token)

            val createResponse = restTemplate.exchange(
                "${baseUrl()}/api/b2b/products",
                HttpMethod.POST,
                HttpEntity(createProductBody("-S04"), headers),
                String::class.java,
            )
            val productId = extractId(requireNotNull(createResponse.headers.location?.toString()))

            When("quantity=-1로 restoreStock 호출하면") {
                val restoreBody = objectMapper.writeValueAsString(mapOf("quantity" to -1))
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/b2b/products/$productId/stock/restore",
                    HttpMethod.POST,
                    HttpEntity(restoreBody, headers),
                    String::class.java,
                )

                Then("[S-04] 400 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }
    }
}
