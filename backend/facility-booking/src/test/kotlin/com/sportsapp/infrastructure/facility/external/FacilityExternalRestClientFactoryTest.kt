package com.sportsapp.infrastructure.facility.external

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.time.Duration

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

    Given("팩토리의 connect/read 타임아웃 정책은") {
        Then("bootstrap 의 ExternalRestClientFactory 원본과 동일한 connect 3초 · read 5초로 고정된다") {
            // [W1-01b 리뷰 ③] 두 구현(bootstrap 원본 / facility-booking 복제본) 사이에서 조용히 어긋날 수
            // 있는 정확히 그 값(타임아웃)을 테스트로 고정한다. baseUrl 라우팅만 검증하던 기존 테스트는
            // 이 정책 드리프트를 잡아내지 못했다.
            val connectTimeoutField = FacilityExternalRestClientFactory::class.java
                .getDeclaredField("CONNECT_TIMEOUT")
                .apply { isAccessible = true }
            val readTimeoutField = FacilityExternalRestClientFactory::class.java
                .getDeclaredField("READ_TIMEOUT")
                .apply { isAccessible = true }

            (connectTimeoutField.get(null) as Duration) shouldBe Duration.ofSeconds(3)
            (readTimeoutField.get(null) as Duration) shouldBe Duration.ofSeconds(5)
        }
    }
})
