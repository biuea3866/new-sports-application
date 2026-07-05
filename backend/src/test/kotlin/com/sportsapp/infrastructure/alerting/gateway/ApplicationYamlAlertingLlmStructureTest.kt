package com.sportsapp.infrastructure.alerting.gateway

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import java.io.File
import org.yaml.snakeyaml.Yaml

/**
 * `application.yml`의 `alerting.llm`/`alerting.retention` 블록 중첩 구조 회귀 방지 (INFRA 리뷰 p1).
 *
 * 실측된 사고: `alerting.retention` 블록이 `alerting.llm` 블록 중간(model 다음, read-timeout-seconds·
 * max-tokens 앞)에 끼어들어, 뒤의 두 키가 `alerting.retention.*` 하위로 밀려났다. `LlmProperties`
 * data class 기본값(55L/1024)과 yaml 기본값이 우연히 같아 Spring 바인딩 테스트([LlmPropertiesTest])
 * 만으로는 드러나지 않는다 — 이 테스트는 yaml을 직접 파싱해 어느 부모 블록에 속하는 키인지를
 * 구조적으로 검증한다. `ApplicationYamlObservabilityConfigTest` 선례와 동일하게 Spring 컨텍스트를
 * 띄우지 않고 파일을 직접 읽는다(테스트 클래스패스의 `src/test/resources/application.yml`이
 * `alerting` 블록을 갖고 있지 않아, classloader 기반 리소스 조회는 실제 운영 yaml을 읽지 못한다).
 */
class ApplicationYamlAlertingLlmStructureTest : BehaviorSpec({

    fun loadApplicationYaml(): Map<String, Any?> {
        val file = File("src/main/resources/application.yml")
        val yaml = Yaml()
        @Suppress("UNCHECKED_CAST")
        return yaml.load(file.inputStream()) as Map<String, Any?>
    }

    Given("실제 클래스패스 application.yml의 alerting 블록") {
        val root = loadApplicationYaml()

        @Suppress("UNCHECKED_CAST")
        val alerting = requireNotNull(root["alerting"] as? Map<String, Any?>)

        @Suppress("UNCHECKED_CAST")
        val llm = requireNotNull(alerting["llm"] as? Map<String, Any?>)

        @Suppress("UNCHECKED_CAST")
        val retention = requireNotNull(alerting["retention"] as? Map<String, Any?>)

        When("llm·retention 하위 키를 확인하면") {
            Then("read-timeout-seconds·max-tokens는 alerting.llm 하위에 속한다") {
                llm.keys shouldContain "read-timeout-seconds"
                llm.keys shouldContain "max-tokens"
            }

            Then("read-timeout-seconds·max-tokens는 alerting.retention 하위로 밀려나 있지 않다") {
                retention.keys shouldNotContain "read-timeout-seconds"
                retention.keys shouldNotContain "max-tokens"
            }

            Then("alerting.retention은 자신의 보존 정책 키(days·cron)만 갖는다") {
                retention.keys shouldContain "days"
                retention.keys shouldContain "cron"
            }
        }
    }
})
