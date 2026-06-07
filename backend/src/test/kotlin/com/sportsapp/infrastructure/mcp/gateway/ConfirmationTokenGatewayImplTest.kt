package com.sportsapp.infrastructure.mcp.gateway

import com.sportsapp.domain.mcp.exception.ConfirmationTokenAlreadyConsumedException
import com.sportsapp.domain.mcp.dto.ConfirmationTokenContext
import com.sportsapp.domain.mcp.exception.ConfirmationTokenExpiredException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.time.Duration

class ConfirmationTokenGatewayImplTest : BehaviorSpec({

    val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()

    val context = ConfirmationTokenContext(
        toolName = "cancelBooking",
        userId = 42L,
        paramsHash = "abc123",
    )

    Given("[U-01] 유효한 context 로 토큰 발급 시") {
        val stringRedisTemplate = mockk<StringRedisTemplate>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        every { stringRedisTemplate.opsForValue() } returns valueOperations
        val gateway = ConfirmationTokenGatewayImpl(stringRedisTemplate, objectMapper)

        val keySlot = slot<String>()
        val ttlSlot = slot<Duration>()
        every { valueOperations.set(capture(keySlot), any(), capture(ttlSlot)) } returns Unit

        When("issue 를 호출하면") {
            val token = gateway.issue(context)

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
        val gateway = ConfirmationTokenGatewayImpl(stringRedisTemplate, objectMapper)

        When("consume 을 호출하면") {
            Then("[U-02] ConfirmationTokenExpiredException 이 발생한다") {
                shouldThrow<ConfirmationTokenExpiredException> {
                    gateway.consume("non-existent-token")
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
        val gateway = ConfirmationTokenGatewayImpl(stringRedisTemplate, objectMapper)

        When("consume 을 호출하면") {
            Then("[U-03] ConfirmationTokenAlreadyConsumedException 이 발생한다") {
                shouldThrow<ConfirmationTokenAlreadyConsumedException> {
                    gateway.consume("already-consumed-token")
                }
            }
        }
    }

    Given("[U-04] issue 를 두 번 호출하면") {
        val stringRedisTemplate = mockk<StringRedisTemplate>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        every { stringRedisTemplate.opsForValue() } returns valueOperations
        every { valueOperations.set(any(), any(), any<Duration>()) } returns Unit
        val gateway = ConfirmationTokenGatewayImpl(stringRedisTemplate, objectMapper)

        When("두 번 연속 issue 를 호출하면") {
            val token1 = gateway.issue(context)
            val token2 = gateway.issue(context)

            Then("[U-04] 매번 다른 UUID 토큰이 발급된다") {
                token1.shouldNotBeNull()
                token2.shouldNotBeNull()
                token1 shouldNotBe token2
                verify(exactly = 2) { valueOperations.set(any(), any(), any<Duration>()) }
            }
        }
    }

    Given("[U-05] 사용자 정의 TTL 로 issue 호출 시") {
        val stringRedisTemplate = mockk<StringRedisTemplate>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        every { stringRedisTemplate.opsForValue() } returns valueOperations
        val ttlSlot = slot<Duration>()
        every { valueOperations.set(any(), any(), capture(ttlSlot)) } returns Unit
        val gateway = ConfirmationTokenGatewayImpl(stringRedisTemplate, objectMapper)

        When("ttl 을 10분으로 지정하여 issue 를 호출하면") {
            gateway.issue(context, Duration.ofMinutes(10))

            Then("[U-05] Redis SETEX TTL 이 10분으로 설정된다") {
                ttlSlot.captured shouldBe Duration.ofMinutes(10)
            }
        }
    }

    Given("[U-06] 유효한 payload 가 Redis 에 저장된 상태로 consume 호출 시") {
        val stringRedisTemplate = mockk<StringRedisTemplate>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        every { stringRedisTemplate.opsForValue() } returns valueOperations
        val serializedContext = """{"toolName":"cancelBooking","userId":42,"paramsHash":"abc123"}"""
        every { valueOperations.getAndDelete(any()) } returns serializedContext
        every { valueOperations.set(any(), any(), any<Duration>()) } returns Unit
        val gateway = ConfirmationTokenGatewayImpl(stringRedisTemplate, objectMapper)

        When("consume 을 호출하면") {
            val consumedContext = gateway.consume("some-token")

            Then("[U-06] context 가 정확히 역직렬화되어 반환된다") {
                consumedContext.toolName shouldBe "cancelBooking"
                consumedContext.userId shouldBe 42L
                consumedContext.paramsHash shouldBe "abc123"
            }
        }
    }

    Given("[U-07] consume 후 소진 마커 기록 여부 확인") {
        val stringRedisTemplate = mockk<StringRedisTemplate>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        every { stringRedisTemplate.opsForValue() } returns valueOperations
        val serializedContext = """{"toolName":"cancelBooking","userId":42,"paramsHash":"abc123"}"""
        every { valueOperations.getAndDelete(any()) } returns serializedContext
        val markerKeySlot = slot<String>()
        every { valueOperations.set(capture(markerKeySlot), any(), any<Duration>()) } returns Unit
        val gateway = ConfirmationTokenGatewayImpl(stringRedisTemplate, objectMapper)

        When("consume 을 호출하면") {
            gateway.consume("test-token")

            Then("[U-07] consumed 마커가 mcp:confirm:consumed:{token} 키로 저장된다") {
                markerKeySlot.captured shouldBe "mcp:confirm:consumed:test-token"
            }
        }
    }
})
