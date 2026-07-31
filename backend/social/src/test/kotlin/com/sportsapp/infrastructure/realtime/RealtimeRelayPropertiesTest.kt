package com.sportsapp.infrastructure.realtime

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

/**
 * social 인스턴스 간 릴레이 설정값 검증 (W1-07 — 티켓 롤백 절: `@ConfigurationProperties`
 * 기본값 + env 로만 전환).
 */
@Configuration
@EnableConfigurationProperties(RealtimeRelayProperties::class)
class RealtimeRelayPropertiesTestConfig

class RealtimeRelayPropertiesTest : BehaviorSpec({

    fun contextRunner() = ApplicationContextRunner()
        .withUserConfiguration(RealtimeRelayPropertiesTestConfig::class.java)

    Given("기본값으로 생성할 때") {
        When("생성하면") {
            val properties = RealtimeRelayProperties()

            Then("릴레이가 기본 활성화(enabled=true)된다") {
                properties.enabled shouldBe true
            }

            Then("채널이 social 전용 네임스페이스 prefix를 사용한다 (§3-2 허용 ⑤)") {
                properties.channel shouldStartWith "social:"
            }
        }
    }

    Given("env로 chat.realtime.relay.enabled=false를 주입한 경우 (롤백 경로)") {
        When("ApplicationContextRunner로 컨텍스트를 로드하면") {
            Then("빈의 enabled 값이 false로 반영된다") {
                contextRunner()
                    .withPropertyValues("chat.realtime.relay.enabled=false")
                    .run { context ->
                        val properties = context.getBean(RealtimeRelayProperties::class.java)
                        properties.enabled shouldBe false
                    }
            }
        }
    }

    Given("env로 채널명을 재정의한 경우") {
        When("ApplicationContextRunner로 컨텍스트를 로드하면") {
            Then("재정의한 채널명이 그대로 반영된다") {
                contextRunner()
                    .withPropertyValues("chat.realtime.relay.channel=social:realtime:relay:custom")
                    .run { context ->
                        val properties = context.getBean(RealtimeRelayProperties::class.java)
                        properties.channel shouldBe "social:realtime:relay:custom"
                    }
            }
        }
    }
})
