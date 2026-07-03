package com.sportsapp.infrastructure.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * application.yml의 management 블록이 관측 스택 계약(BE-02, ADR-001·ADR-005)을
 * 만족하는지 검증한다. Spring 컨텍스트를 띄우지 않고 YAML 파싱만으로 확인해
 * 공유 worktree에서의 Testcontainers/풀 컨텍스트 경합을 회피한다.
 */
class ApplicationYamlObservabilityConfigTest : BehaviorSpec({

    fun loadApplicationYaml(): Map<String, Any?> {
        val file = File("src/main/resources/application.yml")
        val yaml = Yaml()
        @Suppress("UNCHECKED_CAST")
        return yaml.load(file.inputStream()) as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    fun Map<String, Any?>.nested(vararg keys: String): Any? {
        var current: Any? = this
        for (key in keys) {
            current = (current as? Map<String, Any?>)?.get(key) ?: return null
        }
        return current
    }

    Given("application.yml의 management 블록") {
        val root = loadApplicationYaml()
        val management = root["management"] as Map<String, Any?>

        When("datadog export 설정을 확인하면") {
            Then("management.datadog 키가 존재하지 않는다") {
                management.containsKey("datadog") shouldBe false
            }
        }

        When("prometheus/metrics 엔드포인트 노출 설정을 확인하면") {
            val include = root.nested("management", "endpoints", "web", "exposure", "include") as String

            Then("prometheus와 metrics가 노출 목록에 포함된다") {
                val exposed = include.split(",").map { it.trim() }
                exposed shouldContain "prometheus"
                exposed shouldContain "metrics"
            }
        }

        When("metrics.tags.env 설정을 확인하면") {
            val envTag = root.nested("management", "metrics", "tags", "env") as String

            Then("기존 APP_ENV 키를 재사용한다 (ADR-005, 신규 키 미도입)") {
                envTag shouldBe "\${APP_ENV:local}"
            }
        }

        When("tracing sampling 설정을 확인하면") {
            val probability = root.nested("management", "tracing", "sampling", "probability")

            Then("100% 샘플링(1.0)으로 로드된다") {
                (probability as Number).toDouble() shouldBe 1.0
            }
        }

        When("otlp tracing endpoint 설정을 확인하면") {
            val endpoint = root.nested("management", "otlp", "tracing", "endpoint") as String

            Then("MANAGEMENT_OTLP_TRACING_ENDPOINT 환경변수를 HTTP 포트(4318)+경로 기본값과 함께 참조한다 - Spring Boot 3.3.5 HTTP 전용") {
                endpoint shouldBe "\${MANAGEMENT_OTLP_TRACING_ENDPOINT:http://localhost:4318/v1/traces}"
            }
        }

        When("otel resource attribute의 env 매핑을 확인하면") {
            val deploymentEnvironment =
                root.nested("management", "opentelemetry", "resource-attributes", "deployment.environment") as String

            Then("APP_ENV 를 deployment.environment 로 매핑한다") {
                deploymentEnvironment shouldBe "\${APP_ENV:local}"
            }
        }
    }
})
