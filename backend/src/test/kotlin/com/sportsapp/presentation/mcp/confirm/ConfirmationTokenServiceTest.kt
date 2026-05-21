package com.sportsapp.presentation.mcp.confirm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

class ConfirmationTokenServiceTest : BehaviorSpec({

    val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerModule(KotlinModule.Builder().build())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    val context = ConfirmationTokenContext(
        toolName = "cancelBooking",
        userId = 42L,
        paramsHash = "abc123",
    )

    Given("[U-01] 유효한 context 로 토큰 발급 시") {
        val stringRedisTemplate = mockk<StringRedisTemplate>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        every { stringRedisTemplate.opsForValue() } returns valueOperations
        val service = ConfirmationTokenService(stringRedisTemplate, objectMapper)

        val keySlot = slot<String>()
        val ttlSlot = slot<Duration>()
        every { valueOperations.set(capture(keySlot), any(), capture(ttlSlot)) } returns Unit

        When("issue 를 호출하면") {
            val token = service.issue(context)

            Then("[U-01] UUID 형식의 토큰이 반환되고 Redis 에 mcp:confirm:{token} 키로 저장된다") {
                token.shouldNotBeNull()
                token.length shouldBe 36
                keySlot.captured shouldBe "mcp:confirm:$token"
                ttlSlot.captured shouldBe Duration.ofMinutes(5)
            }
        }
    }

    Given("[U-02] 만료되어 Redis 에 키가 없는 토큰으로 consume 시") {
        val stringRedisTemplate = mockk<StringRedisTemplate>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        every { stringRedisTemplate.opsForValue() } returns valueOperations
        every { valueOperations.getAndDelete(any()) } returns null
        every { stringRedisTemplate.hasKey(any()) } returns false
        val service = ConfirmationTokenService(stringRedisTemplate, objectMapper)

        When("consume 을 호출하면") {
            Then("[U-02] ConfirmationTokenExpiredException 이 발생한다") {
                shouldThrow<ConfirmationTokenExpiredException> {
                    service.consume("non-existent-token")
                }
            }
        }
    }

    Given("[U-03] 이미 소진된 토큰으로 consume 시도 시") {
        val stringRedisTemplate = mockk<StringRedisTemplate>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        every { stringRedisTemplate.opsForValue() } returns valueOperations
        every { valueOperations.getAndDelete(any()) } returns null
        every { stringRedisTemplate.hasKey(any()) } returns true
        val service = ConfirmationTokenService(stringRedisTemplate, objectMapper)

        When("consume 을 호출하면") {
            Then("[U-03] ConfirmationTokenAlreadyConsumedException 이 발생한다") {
                shouldThrow<ConfirmationTokenAlreadyConsumedException> {
                    service.consume("already-consumed-token")
                }
            }
        }
    }

    Given("[U-04] verify 호출 시 context 가 일치하는 경우") {
        val stringRedisTemplate = mockk<StringRedisTemplate>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        every { stringRedisTemplate.opsForValue() } returns valueOperations
        val serializedContext = """{"toolName":"cancelBooking","userId":42,"paramsHash":"abc123"}"""
        every { valueOperations.getAndDelete(any()) } returns serializedContext
        every { valueOperations.set(any(), any(), any<Duration>()) } returns Unit
        val service = ConfirmationTokenService(stringRedisTemplate, objectMapper)

        When("verify 를 동일한 context 로 호출하면") {
            val result = service.verify("some-token", context)

            Then("[U-04] true 를 반환한다") {
                result.shouldBeTrue()
            }
        }
    }

    Given("[U-05] verify 호출 시 context 가 불일치하는 경우") {
        val stringRedisTemplate = mockk<StringRedisTemplate>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        every { stringRedisTemplate.opsForValue() } returns valueOperations
        val differentContext = ConfirmationTokenContext(
            toolName = "cancelBooking",
            userId = 42L,
            paramsHash = "different-hash",
        )
        val serializedContext = """{"toolName":"cancelBooking","userId":42,"paramsHash":"abc123"}"""
        every { valueOperations.getAndDelete(any()) } returns serializedContext
        every { valueOperations.set(any(), any(), any<Duration>()) } returns Unit
        val service = ConfirmationTokenService(stringRedisTemplate, objectMapper)

        When("verify 를 다른 context 로 호출하면") {
            val result = service.verify("some-token", differentContext)

            Then("[U-05] false 를 반환한다") {
                result.shouldBeFalse()
            }
        }
    }

    Given("[U-06] issue 를 두 번 호출하면") {
        val stringRedisTemplate = mockk<StringRedisTemplate>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        every { stringRedisTemplate.opsForValue() } returns valueOperations
        every { valueOperations.set(any(), any(), any<Duration>()) } returns Unit
        val service = ConfirmationTokenService(stringRedisTemplate, objectMapper)

        When("두 번 연속 issue 를 호출하면") {
            val token1 = service.issue(context)
            val token2 = service.issue(context)

            Then("[U-06] 매번 다른 UUID 토큰이 발급된다") {
                token1.shouldNotBeNull()
                token2.shouldNotBeNull()
                token1 shouldNotBe token2
                verify(exactly = 2) { valueOperations.set(any(), any(), any<Duration>()) }
            }
        }
    }

    Given("[U-07] 사용자 정의 TTL 로 issue 호출 시") {
        val stringRedisTemplate = mockk<StringRedisTemplate>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        every { stringRedisTemplate.opsForValue() } returns valueOperations
        val ttlSlot = slot<Duration>()
        every { valueOperations.set(any(), any(), capture(ttlSlot)) } returns Unit
        val service = ConfirmationTokenService(stringRedisTemplate, objectMapper)

        When("ttl 을 10분으로 지정하여 issue 를 호출하면") {
            service.issue(context, Duration.ofMinutes(10))

            Then("[U-07] Redis SETEX TTL 이 10분으로 설정된다") {
                ttlSlot.captured shouldBe Duration.ofMinutes(10)
            }
        }
    }
})
