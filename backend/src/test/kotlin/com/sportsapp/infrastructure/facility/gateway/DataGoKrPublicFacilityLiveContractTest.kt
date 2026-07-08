package com.sportsapp.infrastructure.facility.gateway

import com.sportsapp.infrastructure.external.ExternalContractSupport
import com.sportsapp.infrastructure.external.ExternalRestClientFactory
import com.sportsapp.infrastructure.external.Live
import io.kotest.core.Tag
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldNotBeBlank

private const val DATA_GO_KR_SERVICE_KEY_ENV = "DATA_GO_KR_SERVICE_KEY"
private const val PUBLIC_FACILITY_BASE_URL_ENV = "EXTERNAL_PUBLIC_FACILITY_BASE_URL"
private const val DEFAULT_LIVE_BASE_URL = "https://apis.data.go.kr"

/**
 * data.go.kr 공공체육시설 목록 API live 스모크(BE-02, ADR-002).
 *
 * `DATA_GO_KR_SERVICE_KEY` 가 env 에 있을 때만 실 API 를 1페이지 호출해 무손실 역직렬화를 확인한다.
 * 키가 없으면 검증 없이 통과 처리해 기본 `test` 태스크와 `verifyExternalLive` 모두를 붉게 만들지 않는다.
 * base-url 은 `EXTERNAL_PUBLIC_FACILITY_BASE_URL` env 를 우선하고(운영 설정과 동일 규약), 없으면
 * 공공데이터포털 기본 host 로 대체한다.
 */
class DataGoKrPublicFacilityLiveContractTest : BehaviorSpec({

    Given("DATA_GO_KR_SERVICE_KEY 가 env 에 설정된 상태에서") {
        When("verifyExternalLive 로 공공체육시설 1페이지를 실제로 호출하면") {
            Then("무손실로 역직렬화되어 PublicFacility 로 매핑된다") {
                val liveServiceKey = ExternalContractSupport.requireLiveKey(DATA_GO_KR_SERVICE_KEY_ENV)
                if (liveServiceKey == null) {
                    return@Then
                }

                val liveBaseUrl = System.getenv(PUBLIC_FACILITY_BASE_URL_ENV) ?: DEFAULT_LIVE_BASE_URL
                val properties = PublicFacilityProperties(baseUrl = liveBaseUrl, apiKey = liveServiceKey)
                val gateway = DataGoKrPublicFacilityGatewayImpl(ExternalRestClientFactory(), properties)

                val facilities = gateway.fetchPage(1, 10)

                facilities.forEach { facility ->
                    facility.externalId.shouldNotBeBlank()
                    facility.name.shouldNotBeBlank()
                }
            }
        }
    }
}) {
    override fun tags(): Set<Tag> = setOf(Live)
}
