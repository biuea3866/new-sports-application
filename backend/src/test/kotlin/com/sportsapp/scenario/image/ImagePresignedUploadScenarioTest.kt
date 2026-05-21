package com.sportsapp.scenario.image

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.user.LoginResponse
import com.sportsapp.domain.user.UserDomainService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
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
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class ImagePresignedUploadScenarioTest(
    @Autowired private val userDomainService: UserDomainService,
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

    private fun loginAndGetToken(email: String, password: String): String {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val response = restTemplate.exchange(
            "${baseUrl()}/auth/login",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java,
        )
        return objectMapper.readValue(response.body, LoginResponse::class.java).accessToken
    }

    init {
        Given("등록된 사용자가 인증 토큰을 보유한 상태에서") {
            userDomainService.register("image-test@example.com", "ValidPass123!")
            val accessToken = loginAndGetToken("image-test@example.com", "ValidPass123!")

            val authHeaders = HttpHeaders().apply {
                setBearerAuth(accessToken)
                contentType = MediaType.APPLICATION_JSON
            }

            When("[S-01] POST /images/presigned-upload → PUT 업로드를 순서대로 수행하면") {
                val requestBody = """{"filename":"test.jpg","contentType":"image/jpeg","domain":"user"}"""
                val presignedResponse = restTemplate.exchange(
                    "${baseUrl()}/images/presigned-upload",
                    HttpMethod.POST,
                    HttpEntity(requestBody, authHeaders),
                    Map::class.java,
                )

                presignedResponse.statusCode shouldBe HttpStatus.CREATED
                val responseBody = presignedResponse.body
                val uploadUrl = responseBody?.get("url") as? String ?: ""
                val key = responseBody?.get("key") as? String ?: ""

                uploadUrl.shouldNotBeEmpty()
                key.shouldNotBeEmpty()

                val fileContent = ByteArray(256) { 42 }
                val httpClient = HttpClient.newHttpClient()
                val putRequest = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Content-Type", "image/jpeg")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(fileContent))
                    .build()
                val putResponse = httpClient.send(putRequest, HttpResponse.BodyHandlers.ofString())

                Then("[S-01] URL 발급 → PUT 업로드가 한 흐름에서 성공한다") {
                    presignedResponse.statusCode shouldBe HttpStatus.CREATED
                    putResponse.statusCode() shouldBe 200
                }
            }

            When("[S-02] contentType이 text/plain인 요청을 보내면") {
                val requestBody = """{"filename":"doc.txt","contentType":"text/plain","domain":"user"}"""
                val response = restTemplate.exchange(
                    "${baseUrl()}/images/presigned-upload",
                    HttpMethod.POST,
                    HttpEntity(requestBody, authHeaders),
                    Map::class.java,
                )

                Then("[S-02] 400 UnsupportedContentType 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }

        Given("인증 헤더 없이") {
            val noAuthHeaders = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }

            When("[S-04] POST /images/presigned-upload를 호출하면") {
                val requestBody = """{"filename":"test.jpg","contentType":"image/jpeg","domain":"user"}"""
                val response = restTemplate.exchange(
                    "${baseUrl()}/images/presigned-upload",
                    HttpMethod.POST,
                    HttpEntity(requestBody, noAuthHeaders),
                    Map::class.java,
                )

                Then("[S-04] 401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }
    }
}
