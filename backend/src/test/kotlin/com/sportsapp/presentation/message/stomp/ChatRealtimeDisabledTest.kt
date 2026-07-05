package com.sportsapp.presentation.message.stomp

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.message.service.MessageDomainService
import com.sportsapp.domain.user.gateway.JwtIssuer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.util.concurrent.TimeUnit
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.test.context.TestPropertySource
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient

/**
 * chat.realtime.enabled=false 롤백 시나리오 검증 (BE-04, 별도 Spring 컨텍스트).
 * /ws STOMP endpoint 는 미등록(404)이지만 REST 채팅 경로는 그대로 동작해야 한다.
 */
@TestPropertySource(properties = ["chat.realtime.enabled=false"])
class ChatRealtimeDisabledTest(
    @Autowired private val messageDomainService: MessageDomainService,
    @Autowired private val jwtIssuer: JwtIssuer,
    @LocalServerPort private val port: Int,
) : BaseJpaIntegrationTest() {

    private val restTemplate = RestTemplate(
        HttpComponentsClientHttpRequestFactory(HttpClients.createDefault()),
    ).apply {
        errorHandler = object : ResponseErrorHandler {
            override fun hasError(response: ClientHttpResponse): Boolean = false
            override fun handleError(response: ClientHttpResponse) = Unit
        }
    }

    init {
        Given("chat.realtime.enabled=false 인 컨텍스트에서") {
            When("/ws 로 STOMP 연결을 시도하면") {
                val client = WebSocketStompClient(StandardWebSocketClient()).apply {
                    defaultHeartbeat = longArrayOf(0, 0)
                }

                Then("STOMP endpoint 가 등록되지 않아 연결이 실패한다") {
                    shouldThrow<Exception> {
                        client.connectAsync(
                            "ws://localhost:$port/ws",
                            WebSocketHttpHeaders(),
                            StompHeaders(),
                            object : StompSessionHandlerAdapter() {},
                        ).get(5, TimeUnit.SECONDS)
                    }
                    client.stop()
                }
            }

            When("REST 로 방을 만들고 메시지를 보내면") {
                val room = messageDomainService.createGroupRoom("플래그 OFF REST 방", emptyList())
                messageDomainService.joinRoom(room.id, userId = 9101L)
                val accessToken = jwtIssuer.generateAccessToken(
                    userId = 9101L,
                    email = "chat-realtime-disabled@example.com",
                    roles = emptyList(),
                )

                val headers = HttpHeaders().apply {
                    set("Content-Type", "application/json")
                    set("Authorization", "Bearer $accessToken")
                }
                val response = restTemplate.exchange(
                    "http://localhost:$port/rooms/${room.id}/messages",
                    HttpMethod.POST,
                    HttpEntity("""{"content":"REST 는 정상 동작"}""", headers),
                    String::class.java,
                )

                Then("REST 채팅 경로는 정상 동작한다") {
                    response.statusCode shouldBe HttpStatus.CREATED
                }
            }
        }
    }
}
