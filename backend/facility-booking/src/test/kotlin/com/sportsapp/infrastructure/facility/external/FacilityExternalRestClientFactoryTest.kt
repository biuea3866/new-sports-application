package com.sportsapp.infrastructure.facility.external

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class FacilityExternalRestClientFactoryTest : BehaviorSpec({

    Given("baseUrl 로 RestClient 를 생성하면") {
        val mockWebServer = MockWebServer()
        mockWebServer.start()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        When("생성된 RestClient 로 baseUrl 하위 경로를 호출하면") {
            val restClient = FacilityExternalRestClientFactory().create(mockWebServer.url("/").toString())
            val statusCode = restClient.get()
                .uri("/ping")
                .retrieve()
                .toBodilessEntity()
                .statusCode
                .value()

            Then("baseUrl 이 적용된 요청이 실제로 성공한다") {
                statusCode shouldBe 200
                val recordedRequest = mockWebServer.takeRequest()
                recordedRequest.path shouldBe "/ping"
            }
        }

        mockWebServer.shutdown()
    }
})
