package com.sportsapp.infrastructure.storage

import com.sportsapp.BaseIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class MinioImageStorageGatewayImplTest(
    @Autowired private val minioImageStorageGatewayImpl: MinioImageStorageGatewayImpl,
) : BaseIntegrationTest() {

    init {
        Given("MinIO Testcontainer가 실행 중일 때") {

            When("[R-01] createPresignedUpload를 호출하면") {
                val result = minioImageStorageGatewayImpl.createPresignedUpload(
                    key = "images/test/r01-test.jpg",
                    contentType = "image/jpeg",
                    maxBytes = 1024,
                    expirySeconds = 900,
                )

                Then("유효한 PUT URL이 반환된다") {
                    result.url shouldContain "images/test/r01-test.jpg"
                    result.key shouldBe "images/test/r01-test.jpg"
                    result.requiredHeaders.isNotEmpty() shouldBe true
                }
            }

            When("[R-02] 반환된 presigned PUT URL에 실제 1KB 파일을 업로드하면") {
                val presignedUpload = minioImageStorageGatewayImpl.createPresignedUpload(
                    key = "images/test/r02-upload.jpg",
                    contentType = "image/jpeg",
                    maxBytes = 1024,
                    expirySeconds = 900,
                )

                val fileContent = ByteArray(1024) { 0xFF.toByte() }
                val httpClient = HttpClient.newHttpClient()
                val putRequest = HttpRequest.newBuilder()
                    .uri(URI.create(presignedUpload.url))
                    .header("Content-Type", "image/jpeg")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(fileContent))
                    .build()
                val response = httpClient.send(putRequest, HttpResponse.BodyHandlers.ofString())

                Then("[R-02] MinIO에서 200 응답을 받는다") {
                    response.statusCode() shouldBe 200
                }
            }

            When("[R-03] createPresignedDownload로 발급한 URL로 GET 시") {
                val key = "images/test/r03-download.jpg"
                val fileContent = ByteArray(512) { it.toByte() }

                val uploadPresigned = minioImageStorageGatewayImpl.createPresignedUpload(
                    key = key,
                    contentType = "image/jpeg",
                    maxBytes = 1024,
                    expirySeconds = 900,
                )

                val httpClient = HttpClient.newHttpClient()
                val putRequest = HttpRequest.newBuilder()
                    .uri(URI.create(uploadPresigned.url))
                    .header("Content-Type", "image/jpeg")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(fileContent))
                    .build()
                httpClient.send(putRequest, HttpResponse.BodyHandlers.ofByteArray())

                val downloadUrl = minioImageStorageGatewayImpl.createPresignedDownload(key, expirySeconds = 900)
                val getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .GET()
                    .build()
                val getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofByteArray())

                Then("[R-03] 업로드된 파일 바이트가 일치한다") {
                    getResponse.statusCode() shouldBe 200
                    getResponse.body() shouldBe fileContent
                }
            }
        }
    }
}
