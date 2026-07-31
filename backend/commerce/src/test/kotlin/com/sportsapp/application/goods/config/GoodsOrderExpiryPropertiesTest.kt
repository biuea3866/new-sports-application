package com.sportsapp.application.goods.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

/**
 * W1-11a — [GoodsOrderExpiryProperties]는 빠른 TTL(ttlMinutes)·느린 TTL(readyTtlMinutes) 두
 * 값을 갖는다. `facility-booking`(W1-11c) `BookingExpiryProperties`와 동일한 이유로
 * readyTtlMinutes가 ttlMinutes보다 크지 않으면 결제 진행 중(live)인 주문도 빠른 TTL에 의해
 * 오만료될 수 있어 `require`로 부팅 시점에 실패시킨다.
 *
 * 생성자 직접 호출 테스트만으로는 이 불변조건이 **실제로 Spring 부팅을 막는지**를 검증하지
 * 못한다 — Kotlin 값 객체 바인딩이 JavaBean 바인딩으로 떨어지면 `val`이라 env 재정의가
 * 조용히 무시될 수 있어, [ApplicationContextRunner] + `@EnableConfigurationProperties`로
 * 컨텍스트 기동 자체를 검증한다(`BookingExpiryPropertiesTest` 선례와 동일 패턴).
 */
@Configuration
@EnableConfigurationProperties(GoodsOrderExpiryProperties::class)
class GoodsOrderExpiryPropertiesTestConfig

class GoodsOrderExpiryPropertiesTest : BehaviorSpec({

    fun contextRunner() = ApplicationContextRunner()
        .withUserConfiguration(GoodsOrderExpiryPropertiesTestConfig::class.java)

    Given("readyTtlMinutes가 ttlMinutes보다 클 때") {
        When("생성하면") {
            val properties = GoodsOrderExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 90)

            Then("정상 생성된다") {
                properties.ttlMinutes shouldBe 30
                properties.readyTtlMinutes shouldBe 90
            }
        }
    }

    Given("readyTtlMinutes가 ttlMinutes와 같을 때 (핵심 회귀 — 가드 무력화 방지)") {
        When("생성하면") {
            Then("IllegalArgumentException을 던져 부팅을 실패시킨다") {
                shouldThrow<IllegalArgumentException> {
                    GoodsOrderExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 30)
                }
            }
        }
    }

    Given("readyTtlMinutes가 ttlMinutes보다 작을 때 (핵심 회귀 — 가드 무력화 방지)") {
        When("생성하면") {
            Then("IllegalArgumentException을 던져 부팅을 실패시킨다") {
                shouldThrow<IllegalArgumentException> {
                    GoodsOrderExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 20)
                }
            }
        }
    }

    Given("기본값으로 생성할 때") {
        When("생성하면") {
            val properties = GoodsOrderExpiryProperties()

            Then("readyTtlMinutes(90)가 ttlMinutes(30)보다 커 불변조건을 만족한다") {
                properties.ttlMinutes shouldBe 30
                properties.readyTtlMinutes shouldBe 90
                (properties.readyTtlMinutes > properties.ttlMinutes) shouldBe true
            }
        }
    }

    Given("env로 readyTtlMinutes<=ttlMinutes를 주입한 경우 (불변조건 위반으로 컨텍스트 기동이 실패한다)") {
        When("ApplicationContextRunner로 컨텍스트를 로드하면") {
            Then("빈 생성이 실패해 컨텍스트 기동 자체가 실패한다") {
                contextRunner()
                    .withPropertyValues(
                        "goods.expiry.ttl-minutes=30",
                        "goods.expiry.ready-ttl-minutes=20",
                    )
                    .run { context ->
                        context.startupFailure.shouldNotBeNull()
                    }
            }

            Then("실패 원인 체인에 IllegalArgumentException(불변조건 위반)이 포함된다") {
                contextRunner()
                    .withPropertyValues(
                        "goods.expiry.ttl-minutes=30",
                        "goods.expiry.ready-ttl-minutes=30",
                    )
                    .run { context ->
                        val startupFailure = context.startupFailure
                        startupFailure.shouldNotBeNull()
                        val causeChain = generateSequence(startupFailure as Throwable?) { it.cause }
                        causeChain.any { it is IllegalArgumentException }.shouldBeTrue()
                    }
            }
        }
    }

    Given("env로 ready-ttl-minutes를 재정의한 경우 (생성자 바인딩 방식 검증 — env 재정의가 실제로 반영되는지)") {
        When("ApplicationContextRunner로 컨텍스트를 로드하면") {
            Then("재정의한 값이 빈에 그대로 반영된다 — JavaBean 바인딩으로 떨어져 조용히 무시되지 않는다") {
                contextRunner()
                    .withPropertyValues(
                        "goods.expiry.ttl-minutes=30",
                        "goods.expiry.ready-ttl-minutes=180",
                    )
                    .run { context ->
                        val properties = context.getBean(GoodsOrderExpiryProperties::class.java)
                        properties.ttlMinutes shouldBe 30
                        properties.readyTtlMinutes shouldBe 180
                    }
            }
        }
    }

    Given("env가 전혀 주입되지 않은 기본 상태") {
        When("ApplicationContextRunner로 컨텍스트를 로드하면") {
            Then("코드 상 기본값(ttlMinutes=30, readyTtlMinutes=90)으로 정상 기동한다") {
                contextRunner()
                    .run { context ->
                        val properties = context.getBean(GoodsOrderExpiryProperties::class.java)
                        properties.ttlMinutes shouldBe 30
                        properties.readyTtlMinutes shouldBe 90
                    }
            }
        }
    }
})
