package com.sportsapp.infrastructure.virtualqueue.token

import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * `HmacEntryTokenGateway` 순수 서명·검증 로직 단위 검증(Redis 미접근 경로) — BE-03.
 *
 * `RedisDistributedLockFailureTest` 의 mockk 기반 경량 단위 테스트 패턴을 따른다. 실 Redis가
 * 필요한 `issueIfAbsent` 라운드트립·멱등 검증은 `HmacEntryTokenGatewayRedisIntegrationTest`(Testcontainers)에서 다룬다.
 */
class HmacEntryTokenGatewayTest : BehaviorSpec({

    val secret = "test-virtual-queue-hmac-secret-value"
    val target = QueueTarget(type = QueueTargetType.LIMITED_DROP, targetId = 501L)
    val userId = 8842L

    fun buildGateway(redisTemplate: StringRedisTemplate = mockk(), signingSecret: String = secret) =
        HmacEntryTokenGateway(redisTemplate = redisTemplate, secret = signingSecret, ttlSeconds = 300L)

    Given("빈 문자열 secret으로 게이트웨이를 생성하면") {
        When("생성자를 호출하면") {
            Then("약한 기본값 방지를 위해 생성이 실패한다") {
                shouldThrow<IllegalArgumentException> {
                    buildGateway(signingSecret = "")
                }
            }
        }
    }

    Given("공백만 있는 secret으로 게이트웨이를 생성하면") {
        When("생성자를 호출하면") {
            Then("생성이 실패한다") {
                shouldThrow<IllegalArgumentException> {
                    buildGateway(signingSecret = "   ")
                }
            }
        }
    }

    Given("정상 secret으로 만든 게이트웨이에서 mintStateless로 발급한 토큰이 있으면") {
        val gateway = buildGateway()
        val token = gateway.mintStateless(target, userId)

        When("발급 즉시 verify를 호출하면") {
            val result = gateway.verify(target.type.slug, target.targetId, userId, token.raw)

            Then("서명·target·userId가 모두 일치해 true를 반환한다") {
                result shouldBe true
            }
        }
    }

    Given("mintStateless 로 발급한 토큰의 서명 바이트를 변조하면") {
        val gateway = buildGateway()
        val token = gateway.mintStateless(target, userId)
        val separatorIndex = token.raw.indexOf('.')
        val payloadPart = token.raw.substring(0, separatorIndex)
        val signaturePart = token.raw.substring(separatorIndex + 1)
        val tamperedSignature = if (signaturePart.first() == 'A') "B" + signaturePart.drop(1) else "A" + signaturePart.drop(1)
        val tamperedRaw = "$payloadPart.$tamperedSignature"

        When("변조된 토큰으로 verify를 호출하면") {
            val result = gateway.verify(target.type.slug, target.targetId, userId, tamperedRaw)

            Then("위조로 판단해 false를 반환한다") {
                result shouldBe false
            }
        }
    }

    Given("만료 시각이 이미 지난 상태를 흉내낸 토큰이 있으면") {
        val gateway = buildGateway()

        When("발급 후 검증 시점에 만료로 판정되도록 ttlSeconds가 음수인 게이트웨이로 발급하면") {
            val expiredIssuingGateway = HmacEntryTokenGateway(
                redisTemplate = mockk(),
                secret = secret,
                ttlSeconds = -60L,
            )
            val expiredToken = expiredIssuingGateway.mintStateless(target, userId)
            val result = gateway.verify(target.type.slug, target.targetId, userId, expiredToken.raw)

            Then("만료로 판단해 false를 반환한다") {
                result shouldBe false
            }
        }
    }

    Given("mintStateless로 발급한 토큰이 있으면") {
        val gateway = buildGateway()
        val token = gateway.mintStateless(target, userId)

        When("payload의 userId와 다른 userId로 verify를 호출하면") {
            val result = gateway.verify(target.type.slug, target.targetId, userId + 1, token.raw)

            Then("타 사용자 토큰 도용으로 판단해 false를 반환한다") {
                result shouldBe false
            }
        }

        When("payload의 targetId와 다른 targetId로 verify를 호출하면") {
            val result = gateway.verify(target.type.slug, target.targetId + 1, userId, token.raw)

            Then("false를 반환한다") {
                result shouldBe false
            }
        }

        When("payload의 targetType과 다른 targetType으로 verify를 호출하면") {
            val result = gateway.verify(QueueTargetType.TICKETING_EVENT.slug, target.targetId, userId, token.raw)

            Then("false를 반환한다") {
                result shouldBe false
            }
        }
    }

    Given("rawToken이 null이거나 형식이 깨진 경우") {
        val gateway = buildGateway()

        When("null 토큰으로 verify를 호출하면") {
            Then("예외 없이 false를 반환한다") {
                gateway.verify(target.type.slug, target.targetId, userId, null) shouldBe false
            }
        }

        When("점(.) 구분자가 없는 토큰으로 verify를 호출하면") {
            Then("예외 없이 false를 반환한다") {
                gateway.verify(target.type.slug, target.targetId, userId, "not-a-valid-token") shouldBe false
            }
        }

        When("base64 디코딩이 불가능한 문자로 구성된 토큰으로 verify를 호출하면") {
            Then("예외 없이 false를 반환한다") {
                gateway.verify(target.type.slug, target.targetId, userId, "@@@.@@@") shouldBe false
            }
        }
    }

    Given("mintStateless를 호출하면") {
        val redisTemplate = mockk<StringRedisTemplate>()
        val gateway = buildGateway(redisTemplate = redisTemplate)

        When("정상 상태에서 호출하면") {
            gateway.mintStateless(target, userId)

            Then("Redis에 전혀 접근하지 않는다 (§0-3 fail-open 전용 경로)") {
                verify(exactly = 0) { redisTemplate.opsForValue() }
            }
        }
    }

    Given("Redis 연결이 끊긴 상태(모든 호출에서 예외를 던지도록 mock)에서") {
        val redisTemplate = mockk<StringRedisTemplate>()
        every { redisTemplate.opsForValue() } throws RedisConnectionFailureException("connection refused")
        val gateway = buildGateway(redisTemplate = redisTemplate)

        When("mintStateless를 호출하면") {
            Then("예외 없이 토큰을 반환한다 (issueIfAbsent와 대조되는 fail-open 경로)") {
                val token = gateway.mintStateless(target, userId)
                token.raw.isBlank() shouldBe false
                verify(exactly = 0) { redisTemplate.opsForValue() }
            }
        }
    }
})
