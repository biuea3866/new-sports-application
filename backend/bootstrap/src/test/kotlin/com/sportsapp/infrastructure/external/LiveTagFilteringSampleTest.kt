package com.sportsapp.infrastructure.external

import io.kotest.core.Tag
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * live 태그 스펙 선택 배선(BE-01)을 증명하는 최소 샘플.
 * 실제 벤더 계약 live 스모크는 BE-02/03/04 가 각자의 스펙에 [Live] 태그를 부여해 추가한다.
 *
 * - 기본 `./gradlew test` (kotest.tags=!Live) 는 이 스펙을 제외한다.
 * - `./gradlew verifyExternalLive` (kotest.tags=Live) 만 이 스펙을 선택 실행한다.
 */
class LiveTagFilteringSampleTest : BehaviorSpec({

    Given("실 API 키가 env 에 없는 상태에서") {
        When("live 태그 스모크가 verifyExternalLive 로 실행되면") {
            Then("requireLiveKey 가 null 을 반환해 검증 없이 통과 처리한다") {
                val liveApiKey = ExternalContractSupport.requireLiveKey(
                    "SPORTS_APP_LIVE_TAG_FILTERING_SAMPLE_UNDEFINED_KEY",
                )

                liveApiKey shouldBe null
            }
        }
    }
}) {
    override fun tags(): Set<Tag> = setOf(Live)
}
