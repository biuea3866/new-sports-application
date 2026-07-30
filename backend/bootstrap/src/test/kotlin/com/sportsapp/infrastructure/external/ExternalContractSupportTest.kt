package com.sportsapp.infrastructure.external

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse

class ExternalContractSupportTest : BehaviorSpec({

    val httpClient = OkHttpClient()

    Given("startMockServer 로 기동한 서버에 응답을 enqueue 하면") {
        When("그 서버 URL 로 GET 요청을 보내면") {
            Then("enqueue 한 응답 본문이 그대로 반환된다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                mockWebServer.enqueue(MockResponse().setBody("""{"status":"ok"}"""))

                val request = Request.Builder().url(mockWebServer.url("/health")).build()
                val response = httpClient.newCall(request).execute()
                val responseBody = requireNotNull(response.body).string()

                responseBody shouldBe """{"status":"ok"}"""

                response.close()
                mockWebServer.shutdown()
            }
        }
    }

    Given("존재하는 fixture 경로") {
        When("loadFixture 로 읽으면") {
            Then("비어 있지 않은 JSON 문자열을 반환한다") {
                val fixtureContent = ExternalContractSupport.loadFixture("external/sample/health-check.json")

                fixtureContent.shouldNotBeBlank()
            }
        }
    }

    Given("존재하지 않는 fixture 경로") {
        When("loadFixture 를 호출하면") {
            Then("경로를 포함한 명확한 예외 메시지로 실패한다") {
                val missingFixturePath = "external/sample/does-not-exist.json"

                val exception = shouldThrow<IllegalArgumentException> {
                    ExternalContractSupport.loadFixture(missingFixturePath)
                }

                exception.message?.shouldNotBeBlank()
                exception.message?.contains(missingFixturePath) shouldBe true
            }
        }
    }

    Given("live 스모크에 필요한 환경변수가 설정돼 있지 않으면") {
        When("requireLiveKey 를 호출하면") {
            Then("null 을 반환해 live 스펙이 스킵되도록 한다") {
                val liveApiKey = ExternalContractSupport.requireLiveKey(
                    "SPORTS_APP_EXTERNAL_CONTRACT_SUPPORT_TEST_UNDEFINED_KEY",
                )

                liveApiKey shouldBe null
            }
        }
    }

    Given("live 스모크에 필요한 환경변수가 설정돼 있으면") {
        When("requireLiveKey 를 호출하면") {
            Then("비어 있지 않은 값을 그대로 반환한다") {
                val pathEnvironmentValue = ExternalContractSupport.requireLiveKey("PATH")

                pathEnvironmentValue shouldNotBe null
            }
        }
    }
})
