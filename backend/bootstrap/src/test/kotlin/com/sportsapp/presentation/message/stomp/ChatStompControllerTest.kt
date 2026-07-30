package com.sportsapp.presentation.message.stomp

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.message.repository.MessageRepository
import com.sportsapp.domain.message.service.MessageDomainService
import com.sportsapp.domain.user.gateway.JwtIssuer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient

/**
 * ChatStompController 통합 검증 (BE-04) — chat.realtime.enabled=true(test 기본값) 컨텍스트에서
 * 실 WebSocketStompClient 로 handshake JWT 인증·발화 브로드캐스트·타이핑 브로드캐스트를 검증한다.
 */
class ChatStompControllerTest(
    @Autowired private val messageDomainService: MessageDomainService,
    @Autowired private val messageRepository: MessageRepository,
    @Autowired private val jwtIssuer: JwtIssuer,
    @LocalServerPort private val port: Int,
) : BaseJpaIntegrationTest() {

    private val jsonReader = ObjectMapper()

    private fun tokenFor(userId: Long): String =
        jwtIssuer.generateAccessToken(userId = userId, email = "chat-stomp-$userId@example.com", roles = listOf("USER"))

    private fun newClient(): WebSocketStompClient =
        WebSocketStompClient(StandardWebSocketClient()).apply {
            messageConverter = MappingJackson2MessageConverter()
            defaultHeartbeat = longArrayOf(0, 0)
        }

    private fun connect(client: WebSocketStompClient, token: String?): StompSession {
        val connectHeaders = StompHeaders()
        token?.let { connectHeaders["Authorization"] = "Bearer $it" }
        val future = client.connectAsync(
            "ws://localhost:$port/ws",
            WebSocketHttpHeaders(),
            connectHeaders,
            object : StompSessionHandlerAdapter() {},
        )
        return future.get(5, TimeUnit.SECONDS)
    }

    private fun rawFrameQueue(): LinkedBlockingQueue<Map<*, *>> = LinkedBlockingQueue()

    private fun frameHandler(queue: LinkedBlockingQueue<Map<*, *>>): StompFrameHandler =
        object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders) = ByteArray::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                val bytes = payload as? ByteArray ?: return
                queue.offer(jsonReader.readValue(bytes, Map::class.java))
            }
        }

    init {
        Given("유효한 JWT 를 Authorization 헤더에 담아 CONNECT 하면") {
            val client = newClient()

            When("연결을 시도하면") {
                val session = connect(client, tokenFor(9001L))

                Then("세션이 정상 수립된다 (Principal=userId)") {
                    session.isConnected shouldBe true
                    session.disconnect()
                    client.stop()
                }
            }
        }

        Given("Authorization 헤더 없이 CONNECT 하면") {
            val client = newClient()

            When("연결을 시도하면") {
                Then("연결이 거부된다") {
                    shouldThrow<Exception> {
                        connect(client, null)
                    }
                    client.stop()
                }
            }
        }

        Given("방에 참여 중인 사용자가 실시간 발화하면") {
            val room = messageDomainService.createGroupRoom("실시간 발화 테스트 방", emptyList())
            messageDomainService.joinRoom(room.id, userId = 9002L)
            val client = newClient()
            val session = connect(client, tokenFor(9002L))
            val received = rawFrameQueue()
            session.subscribe("/topic/rooms/${room.id}", frameHandler(received))
            Thread.sleep(300)

            When("/app/rooms/{roomId}/send 로 SEND 하면") {
                session.send("/app/rooms/${room.id}/send", StompSendMessageRequest(content = "실시간 안녕"))
                val message = received.poll(5, TimeUnit.SECONDS)

                Then("커밋 이후 /topic/rooms/{roomId} 구독자가 메시지를 수신한다") {
                    message.shouldNotBeNull()
                    message["content"] shouldBe "실시간 안녕"
                    (message["userId"] as Number).toLong() shouldBe 9002L
                    session.disconnect()
                    client.stop()
                }
            }
        }

        Given("타이핑 이벤트를 보내면") {
            val room = messageDomainService.createGroupRoom("타이핑 테스트 방", emptyList())
            messageDomainService.joinRoom(room.id, userId = 9003L)
            val client = newClient()
            val session = connect(client, tokenFor(9003L))
            val received = rawFrameQueue()
            session.subscribe("/topic/rooms/${room.id}/typing", frameHandler(received))
            Thread.sleep(300)
            val beforeCount = messageRepository.findByRoomId(room.id).size

            When("/app/rooms/{roomId}/typing 으로 SEND 하면") {
                session.send("/app/rooms/${room.id}/typing", StompTypingRequest(typing = true))
                val event = received.poll(5, TimeUnit.SECONDS)

                Then("구독자에게 전달되고 DB 에는 저장되지 않는다") {
                    event.shouldNotBeNull()
                    (event["userId"] as Number).toLong() shouldBe 9003L
                    event["typing"] shouldBe true
                    messageRepository.findByRoomId(room.id).size shouldBe beforeCount
                    session.disconnect()
                    client.stop()
                }
            }
        }
    }
}
