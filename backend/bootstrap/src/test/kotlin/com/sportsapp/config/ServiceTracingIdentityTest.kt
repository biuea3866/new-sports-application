package com.sportsapp.config

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import org.yaml.snakeyaml.Yaml

/**
 * W1-09 — 서비스별 `service.name` 리소스 속성을 코드로 고정한다 (실행설계 §6-1 관측).
 *
 * 분산 추적은 이미 갖춰져 있고 서비스 이름만 갈리면 트레이스가 경계를 넘어 이어진다. 반대로 이름이
 * 하나로 뭉치면 경계 간 호출(C1~C10)을 트레이스에서 **구분할 수 없다** — MSA 에서 가장 먼저 필요한
 * 관측 능력이 조용히 무력화되므로, 값 전사를 테스트로 고정해 드리프트를 막는다.
 *
 * 1단계에서는 6개 프로파일이 활성화되지 않으므로 런타임 영향이 0이다 — 2단계에 활성화될 설정을
 * 미리 확정해 두는 것이 목적이다.
 */
class ServiceTracingIdentityTest : DescribeSpec({

    val repositoryRoot = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "observability/otel-collector-config.yaml").isFile }

    fun repositoryFile(path: String): File = File(requireNotNull(repositoryRoot), path)

    fun readText(path: String): String = repositoryFile(path).readText()

    /** 서비스 → `spring.application.name` 규약. `sports-` 접두사로 web(`sports-web`)과 한 계열을 이룬다. */
    val serviceNames = mapOf(
        "commerce" to "sports-commerce",
        "payment" to "sports-payment",
        "facility-booking" to "sports-facility-booking",
        "social" to "sports-social",
        "platform" to "sports-platform",
        "edge" to "sports-edge",
    )

    describe("설정 루트 탐색") {
        it("observability 설정을 가진 레포 루트를 찾는다") {
            repositoryRoot.shouldNotBeNull()
        }
    }

    describe("모듈별 프로파일의 서비스 이름") {
        serviceNames.forEach { (module, serviceName) ->
            it("$module 프로파일은 $serviceName 을 쓰고 OTEL_SERVICE_NAME 으로 재정의할 수 있다") {
                val yaml = Yaml().load<Map<String, Any>>(
                    readText("backend/$module/src/main/resources/application-$module.yml"),
                )

                @Suppress("UNCHECKED_CAST")
                val spring = yaml["spring"] as Map<String, Any>

                @Suppress("UNCHECKED_CAST")
                val application = spring["application"] as Map<String, Any>

                // env 가 이기는 형태다 — compose 배선(W1-02)과 yml 이 어긋나지 않게 하려는 의도이며,
                // 그래서 2단계 compose 는 OTEL_SERVICE_NAME 을 반드시 서비스별로 설정해야 한다.
                application["name"] shouldBe "\${OTEL_SERVICE_NAME:$serviceName}"
            }
        }
    }

    describe("기존 단일 앱 회귀 보호") {
        val rootApplicationYaml = readText("backend/bootstrap/src/main/resources/application.yml")

        it("프로파일 미활성 기본값은 sports-application 을 유지한다 — 기존 대시보드·로그 쿼리가 깨지지 않는다") {
            rootApplicationYaml shouldContain "name: sports-application"
        }

        it("metrics 공통 태그 service 가 MCP 시절 고정값(mcp)으로 남아 있지 않다") {
            // 확인 근거: observability/grafana/dashboards/*.json 은 `application`·`env`·
            // `resource.service.name` 만 쓰고 `service` 태그를 쿼리하지 않는다. 소비자가 없으므로
            // 6서비스 전개에서 거짓이 되는 고정값을 두지 않는다.
            rootApplicationYaml shouldNotContain "service: mcp"
        }
    }

    describe("otel-collector 가 앱 주입값을 덮지 않는다") {
        val collectorConfig = Yaml().load<Map<String, Any>>(readText("observability/otel-collector-config.yaml"))

        it("service.name 은 부재 시에만 채우는 insert 동작이다 — unknown_service 오염 방지") {
            @Suppress("UNCHECKED_CAST")
            val processors = collectorConfig["processors"] as Map<String, Any>

            @Suppress("UNCHECKED_CAST")
            val resource = processors["resource"] as Map<String, Any>

            @Suppress("UNCHECKED_CAST")
            val attributes = resource["attributes"] as List<Map<String, Any>>

            val serviceNameAttribute = attributes.first { it["key"] == "service.name" }
            serviceNameAttribute["action"] shouldBe "insert"
        }
    }

    describe("Loki 로그 라벨이 서비스별로 갈린다") {
        val promtailConfig = readText("observability/promtail/promtail-config.yaml")

        serviceNames.forEach { (module, serviceName) ->
            it("$module 컨테이너 로그가 $serviceName 라벨로 승격된다") {
                promtailConfig shouldContain """regex: "^$module$""""
                promtailConfig shouldContain """replacement: "$serviceName""""
            }
        }

        it("기존 backend·web 매핑은 유지된다 (회귀)") {
            promtailConfig shouldContain """replacement: "sports-application""""
            promtailConfig shouldContain """replacement: "sports-web""""
        }
    }

    describe("Prometheus scrape 타깃") {
        val prometheusConfig = readText("observability/prometheus/prometheus.yml")

        serviceNames.keys.forEach { module ->
            it("$module 타깃이 주석 상태로 준비돼 있다 — 1단계엔 backend 1개만 뜨므로 활성화하면 scrape 실패 알람이 난다") {
                prometheusConfig shouldContain "# - $module:8080"
            }
        }

        it("활성 타깃은 여전히 backend 하나다") {
            val activeTargets = prometheusConfig.lines()
                .map { it.trim() }
                .filter { it.startsWith("- ") && it.endsWith(":8080") }
            activeTargets shouldBe listOf("- backend:8080")
        }
    }

    describe("Grafana 대시보드 서비스 변수") {
        val springDashboard = readText("observability/grafana/dashboards/spring.json")

        it("service 변수 목록에 6서비스와 기존 2값이 모두 들어 있다") {
            val expected = serviceNames.values + listOf("sports-application", "sports-web")
            val serviceVariableQuery = springDashboard
                .substringAfter(""""name": "service"""")
                .substringAfter(""""query": """")
                .substringBefore("\"")
            expected.forEach { serviceName -> serviceVariableQuery shouldContain serviceName }
        }
    }
})
