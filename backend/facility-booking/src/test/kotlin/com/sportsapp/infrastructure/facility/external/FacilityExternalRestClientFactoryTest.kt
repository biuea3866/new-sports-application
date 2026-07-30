package com.sportsapp.infrastructure.facility.external

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.springframework.web.client.ResourceAccessException
import java.net.SocketTimeoutException
import kotlin.system.measureTimeMillis

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

    Given("팩토리가 생성한 RestClient 의 read 타임아웃 정책은") {
        Then("서버가 응답을 전혀 보내지 않으면 5초 근방에서 실제로 SocketTimeoutException 으로 요청을 끊는다") {
            // [W1-01b 리뷰 재검토(p3-2)] 기존 테스트는 private 상수(CONNECT_TIMEOUT/READ_TIMEOUT)를
            // 리플렉션으로 읽기만 해 ① 그 값이 실제 RestClient 에 적용되는지 ② bootstrap 원본과 동치인지
            // 둘 다 검증하지 못했다. facility-booking 은 bootstrap 을 의존할 수 없어(모듈 방향 위반) ②는
            // 이 모듈 테스트로 직접 검증할 수 없으므로, ①을 MockWebServer 로 실제 타임아웃이 발동하는지
            // 동작 검증으로 대체한다 — bootstrap 원본과의 값 동치는 [FacilityExternalRestClientFactory]
            // KDoc 의 서술적 계약으로만 유지된다(회귀 시 두 구현을 나란히 두고 비교해야 한다).
            //
            // `MockResponse.setBodyDelay(n)`은 실측 결과 헤더까지 지연 응답과 함께 한 번에 flush 돼,
            // 지연이 configure 된 READ_TIMEOUT(5초)보다 길면 예외가 5초가 아니라 지연 시간(n초) 근방에서
            // 발생했다(1차 시도에서 6초·10초 지연으로 각각 실측·재현) — read 타임아웃 자체가 아니라 body
            // 지연 시나리오를 검증하는 셈이라 이 값(READ_TIMEOUT) 자체를 고정하는 목적에 맞지 않는다.
            // 대신 `SocketPolicy.NO_RESPONSE`(서버가 연결만 수락하고 응답을 전혀 쓰지 않음)로 순수하게
            // "응답을 기다리는 읽기"만 블로킹시켜, READ_TIMEOUT 값 자체가 실제로 적용되는지 격리해 검증한다.
            val mockWebServer = MockWebServer()
            mockWebServer.start()
            mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val restClient = FacilityExternalRestClientFactory().create(mockWebServer.url("/").toString())

            var caughtException: Throwable? = null
            val elapsedMillis = measureTimeMillis {
                caughtException = runCatching {
                    restClient.get().uri("/slow").retrieve().toBodilessEntity()
                }.exceptionOrNull()
            }

            caughtException.shouldBeInstanceOf<ResourceAccessException>()
            caughtException?.cause.shouldBeInstanceOf<SocketTimeoutException>()
            caughtException?.message.orEmpty() shouldContain "Read timed out"
            // read 타임아웃 5초 근방(지연·스케줄링 오차 허용 4.5~6.5초)에서 끊겼는지로 값 자체를 검증한다.
            (elapsedMillis in 4_500..6_500) shouldBe true

            mockWebServer.shutdown()
        }
    }
})
