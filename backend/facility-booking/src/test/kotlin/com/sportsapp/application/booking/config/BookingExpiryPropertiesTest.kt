package com.sportsapp.application.booking.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

/**
 * W1-11c 4차 재설계 — [BookingExpiryProperties]는 빠른 TTL(ttlMinutes)·느린 TTL
 * (readyTtlMinutes) 두 값을 갖는다. readyTtlMinutes가 ttlMinutes보다 크지 않으면
 * 결제 진행 중(live)인 예약도 빠른 TTL에 의해 오만료될 수 있으므로, 이 불변조건을
 * `require`로 강제해 부팅 시점에 실패시킨다(env 한 줄 오설정으로 가드가 무력화되는 것을
 * 재기동 즉시 차단 — p2-2 재발 방지).
 *
 * **5차 리뷰 p2**: 생성자 직접 호출 테스트만으로는 이 불변조건이 **실제로 Spring 부팅을
 * 막는지**를 검증하지 못한다 — Kotlin 주 생성자 + 전 파라미터 기본값 조합이 constructor
 * binding이 아니라 JavaBean 바인딩으로 떨어지면 `val`이라 env 재정의가 조용히 무시될 수
 * 있다. `ExternalApiPropertiesConsistencyTest` 선례와 동일하게 [ApplicationContextRunner] +
 * `@EnableConfigurationProperties`로 컨텍스트 기동 자체를 검증한다.
 */
@Configuration
@EnableConfigurationProperties(BookingExpiryProperties::class)
class BookingExpiryPropertiesTestConfig

class BookingExpiryPropertiesTest : BehaviorSpec({

    fun contextRunner() = ApplicationContextRunner()
        .withUserConfiguration(BookingExpiryPropertiesTestConfig::class.java)

    Given("readyTtlMinutes가 ttlMinutes보다 클 때") {
        When("생성하면") {
            val properties = BookingExpiryProperties(ttlMinutes = 15, readyTtlMinutes = 60)

            Then("정상 생성된다") {
                properties.ttlMinutes shouldBe 15
                properties.readyTtlMinutes shouldBe 60
            }
        }
    }

    Given("readyTtlMinutes가 ttlMinutes와 같을 때 (핵심 회귀 — 가드 무력화 방지)") {
        When("생성하면") {
            Then("IllegalArgumentException을 던져 부팅을 실패시킨다") {
                shouldThrow<IllegalArgumentException> {
                    BookingExpiryProperties(ttlMinutes = 15, readyTtlMinutes = 15)
                }
            }
        }
    }

    Given("readyTtlMinutes가 ttlMinutes보다 작을 때 (핵심 회귀 — 가드 무력화 방지)") {
        When("생성하면") {
            Then("IllegalArgumentException을 던져 부팅을 실패시킨다") {
                shouldThrow<IllegalArgumentException> {
                    BookingExpiryProperties(ttlMinutes = 15, readyTtlMinutes = 10)
                }
            }
        }
    }

    Given("기본값으로 생성할 때") {
        When("생성하면") {
            val properties = BookingExpiryProperties()

            Then("readyTtlMinutes(60)가 ttlMinutes(15)보다 커 불변조건을 만족한다") {
                (properties.readyTtlMinutes > properties.ttlMinutes) shouldBe true
            }
        }
    }

    Given("env로 readyTtlMinutes<=ttlMinutes를 주입한 경우 (5차 리뷰 p2 — 컨텍스트 기동 검증)") {
        When("ApplicationContextRunner로 컨텍스트를 로드하면") {
            Then("빈 생성이 실패해 컨텍스트 기동 자체가 실패한다") {
                contextRunner()
                    .withPropertyValues(
                        "booking.expiry.ttl-minutes=15",
                        "booking.expiry.ready-ttl-minutes=10",
                    )
                    .run { context ->
                        context.startupFailure.shouldNotBeNull()
                    }
            }

            Then("실패 원인 체인에 IllegalArgumentException(불변조건 위반)이 포함된다") {
                contextRunner()
                    .withPropertyValues(
                        "booking.expiry.ttl-minutes=15",
                        "booking.expiry.ready-ttl-minutes=15",
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
                        "booking.expiry.ttl-minutes=15",
                        "booking.expiry.ready-ttl-minutes=120",
                    )
                    .run { context ->
                        val properties = context.getBean(BookingExpiryProperties::class.java)
                        properties.ttlMinutes shouldBe 15
                        properties.readyTtlMinutes shouldBe 120
                    }
            }
        }
    }

    Given("env가 전혀 주입되지 않은 기본 상태") {
        When("ApplicationContextRunner로 컨텍스트를 로드하면") {
            Then("코드 상 기본값(ttlMinutes=15, readyTtlMinutes=60)으로 정상 기동한다") {
                contextRunner()
                    .run { context ->
                        val properties = context.getBean(BookingExpiryProperties::class.java)
                        properties.ttlMinutes shouldBe 15
                        properties.readyTtlMinutes shouldBe 60
                    }
            }
        }
    }
})
