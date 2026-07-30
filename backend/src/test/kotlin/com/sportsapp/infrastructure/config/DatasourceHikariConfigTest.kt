package com.sportsapp.infrastructure.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.io.File

/**
 * [FIX-03] HikariCP fail-fast 설정 정합 검증 (0.5단계 실측 리포트 L77-83, 풀 대기 30,018ms).
 *
 * `spring.datasource.hikari.*` 를 Spring 의 실제 설정 데이터 로딩 경로(ConfigDataApplicationContextInitializer)로
 * 해석해, 기본값과 env override 가 둘 다 placeholder 문법(`${VAR:default}`)으로 동작하는지 확인한다.
 * Spring 컨텍스트 전체를 띄우지 않아 공유 worktree의 Testcontainers 경합을 피한다
 * ([ApplicationYamlObservabilityConfigTest] 선례와 동일 원칙, 단 여기는 env override 까지 검증하기 위해
 * [ExternalApiPropertiesConsistencyTest] 의 ApplicationContextRunner 패턴을 재사용한다).
 */
class DatasourceHikariConfigTest : BehaviorSpec({

    val mainApplicationYmlPath = File("src/main/resources/application.yml").absolutePath

    fun contextRunner(vararg properties: String) =
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.config.location=file:$mainApplicationYmlPath")
            .withPropertyValues(*properties)

    Given("env override 가 전혀 없는 기본 상태") {
        When("application.yml 로 spring.datasource.hikari 를 해석하면") {
            Then("maximum-pool-size 기본값은 30 이다 (실측 병목 ② fail-fast)") {
                contextRunner().run { context ->
                    context.environment.getProperty("spring.datasource.hikari.maximum-pool-size") shouldBe "30"
                }
            }

            Then("connection-timeout 기본값은 5000(5s) 이다 (30초 대기 제거)") {
                contextRunner().run { context ->
                    context.environment.getProperty("spring.datasource.hikari.connection-timeout") shouldBe "5000"
                }
            }
        }
    }

    Given("DB_HIKARI_MAXIMUM_POOL_SIZE, DB_HIKARI_CONNECTION_TIMEOUT 이 env 로 주입된 상태") {
        When("application.yml 로 spring.datasource.hikari 를 해석하면") {
            Then("두 값 모두 env 로 재정의된다 (엣지 — W1-03 의 서비스별 차등 배분 대비)") {
                contextRunner(
                    "DB_HIKARI_MAXIMUM_POOL_SIZE=8",
                    "DB_HIKARI_CONNECTION_TIMEOUT=1500",
                ).run { context ->
                    context.environment.getProperty("spring.datasource.hikari.maximum-pool-size") shouldBe "8"
                    context.environment.getProperty("spring.datasource.hikari.connection-timeout") shouldBe "1500"
                }
            }
        }
    }

    Given("validation-timeout, max-lifetime 은 이 티켓의 변경 대상이 아니다") {
        When("application.yml 의 spring.datasource.hikari 블록을 확인하면") {
            Then("validation-timeout, max-lifetime 키가 존재하지 않는다 (근거 없는 변경 금지)") {
                contextRunner().run { context ->
                    context.environment.containsProperty("spring.datasource.hikari.validation-timeout") shouldBe false
                    context.environment.containsProperty("spring.datasource.hikari.max-lifetime") shouldBe false
                }
            }
        }
    }
})
