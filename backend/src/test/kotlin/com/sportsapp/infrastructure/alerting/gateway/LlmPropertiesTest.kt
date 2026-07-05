package com.sportsapp.infrastructure.alerting.gateway

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration

/**
 * `application.yml`의 `alerting.llm.*`가 실제 클래스패스 yaml로부터 `LlmProperties`(prefix
 * `alerting.llm`)에 정상 바인딩되는지 검증한다. `LlmProperties`를 코드로 직접 생성해 검증하면
 * yaml 구조 결함(다른 블록이 `alerting.llm` 블록 중간에 끼어드는 사고 등)을 잡지 못한다 — 이 값이
 * 실제로 어느 yaml 경로에서 왔는지는 이 테스트만으로는 확정할 수 없으므로, 키가 `alerting.llm`
 * 블록이 아닌 다른 블록으로 밀려나 있지 않은지는 [ApplicationYamlAlertingLlmStructureTest]가
 * yaml 구조 자체를 직접 검증한다(두 테스트가 상호 보완).
 *
 * `TestApp`은 일반 `@Configuration`(`@SpringBootApplication`이 아님)이라 컴포넌트 스캔·자동 구성이
 * 전혀 트리거되지 않는다 — 같은 패키지의 `TelemetryQueryGatewayImpl` 등 다른 Gateway 구현체가
 * 요구하는 무관한 빈(`ExternalRestClientFactory` 등) 없이도 최소 컨텍스트로 바인딩만 확인한다.
 */
@SpringBootTest(classes = [LlmPropertiesTest.TestApp::class])
class LlmPropertiesTest(
    @Autowired private val llmProperties: LlmProperties,
) : BehaviorSpec({

    Given("실제 클래스패스 application.yml의 alerting.llm 블록") {
        When("Spring 컨텍스트가 alerting.llm 프리픽스로 LlmProperties를 바인딩하면") {
            Then("application.yml에 선언된 기본값으로 정상 바인딩된다") {
                llmProperties.baseUrl shouldBe "https://api.anthropic.com"
                llmProperties.model shouldBe "claude-fable-5"
                llmProperties.readTimeoutSeconds shouldBe 55L
                llmProperties.maxTokens shouldBe 1024
            }
        }
    }
}) {
    @Configuration
    @EnableConfigurationProperties(LlmProperties::class)
    class TestApp
}
