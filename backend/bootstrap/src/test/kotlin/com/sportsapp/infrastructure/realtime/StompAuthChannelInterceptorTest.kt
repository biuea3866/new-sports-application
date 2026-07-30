package com.sportsapp.infrastructure.realtime

import com.sportsapp.domain.user.gateway.JwtIssuer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.core.AuthenticationException

private const val VALID_TOKEN = "valid-token"
private const val INVALID_TOKEN = "invalid-token"

class StompAuthChannelInterceptorTest : BehaviorSpec({

    val jwtIssuer = mockk<JwtIssuer>()
    val interceptor = StompAuthChannelInterceptor(jwtIssuer)
    val channel = mockk<MessageChannel>()

    fun connectMessage(authorizationHeader: String?): Message<ByteArray> {
        val accessor = StompHeaderAccessor.create(StompCommand.CONNECT)
        authorizationHeader?.let { accessor.addNativeHeader("Authorization", it) }
        accessor.setLeaveMutable(true)
        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    Given("유효한 JWT 로 CONNECT 프레임을 보내면") {
        every { jwtIssuer.validateToken(VALID_TOKEN) } returns true
        every { jwtIssuer.extractUserId(VALID_TOKEN) } returns 42L

        When("preSend 를 호출하면") {
            val message = connectMessage("Bearer $VALID_TOKEN")
            val result = interceptor.preSend(message, channel)

            Then("세션에 Principal=userId 가 설정된다") {
                val resultAccessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor::class.java)
                resultAccessor?.user?.name shouldBe "42"
            }
        }
    }

    Given("Authorization 헤더 없이 CONNECT 프레임을 보내면") {
        When("preSend 를 호출하면") {
            val message = connectMessage(null)

            Then("인증 예외를 던져 CONNECT 가 거부된다") {
                shouldThrow<AuthenticationException> {
                    interceptor.preSend(message, channel)
                }
            }
        }
    }

    Given("무효한 JWT 로 CONNECT 프레임을 보내면") {
        every { jwtIssuer.validateToken(INVALID_TOKEN) } returns false

        When("preSend 를 호출하면") {
            val message = connectMessage("Bearer $INVALID_TOKEN")

            Then("인증 예외를 던져 CONNECT 가 거부된다") {
                shouldThrow<AuthenticationException> {
                    interceptor.preSend(message, channel)
                }
            }
        }
    }

    Given("CONNECT 가 아닌 SEND 프레임을 보내면") {
        When("preSend 를 호출하면") {
            val sendAccessor = StompHeaderAccessor.create(StompCommand.SEND)
            sendAccessor.destination = "/app/rooms/1/send"
            val message = MessageBuilder.createMessage(ByteArray(0), sendAccessor.messageHeaders)

            Then("인증 검사 없이 메시지를 그대로 통과시킨다") {
                interceptor.preSend(message, channel) shouldBe message
            }
        }
    }
})
