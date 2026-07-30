package com.sportsapp.presentation.message.controller

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.application.message.dto.RoomUnreadResponse
import com.sportsapp.domain.message.service.MessageDomainService
import com.sportsapp.domain.user.gateway.JwtIssuer
import io.kotest.matchers.shouldBe
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

/**
 * ReadCursorApiController 통합 검증 (BE-05) — 실 HTTP 호출로 Bearer JWT 인증·읽음 커서 갱신·
 * 안읽은 수 집계 계약을 검증한다.
 */
class ReadCursorApiControllerTest(
    @Autowired private val messageDomainService: MessageDomainService,
    @Autowired private val jwtIssuer: JwtIssuer,
    @Autowired private val objectMapper: ObjectMapper,
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

    private fun baseUrl() = "http://localhost:$port"

    private fun tokenFor(userId: Long): String =
        jwtIssuer.generateAccessToken(userId = userId, email = "read-cursor-$userId@example.com", roles = listOf("USER"))

    private fun authHeaders(userId: Long): HttpHeaders = HttpHeaders().apply {
        setBearerAuth(tokenFor(userId))
        contentType = org.springframework.http.MediaType.APPLICATION_JSON
    }

    init {
        Given("방에 참여 중인 사용자가 읽음 처리를 요청할 때") {
            val room = messageDomainService.createGroupRoom("읽음 커서 테스트 방", emptyList())
            messageDomainService.joinRoom(room.id, userId = 8001L)
            messageDomainService.joinRoom(room.id, userId = 8002L)
            val messages = (1..5).map { index ->
                messageDomainService.sendMessage(room.id, 8002L, "상대 메시지$index")
            }
            val lastMessageId = messages.last().id

            When("POST /rooms/{roomId}/read 로 lastReadMessageId 를 전달하면") {
                val body = objectMapper.writeValueAsString(mapOf("lastReadMessageId" to lastMessageId))
                val response = restTemplate.exchange(
                    "${baseUrl()}/rooms/${room.id}/read",
                    HttpMethod.POST,
                    HttpEntity(body, authHeaders(8001L)),
                    String::class.java,
                )

                Then("200 OK 와 함께 안읽은 수 0 이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val unread = objectMapper.readValue(response.body, RoomUnreadResponse::class.java)
                    unread.roomId shouldBe room.id
                    unread.unreadCount shouldBe 0L
                }
            }
        }

        Given("서로 다른 두 방에 참여 중인 사용자") {
            val roomA = messageDomainService.createGroupRoom("안읽은 방 A", emptyList())
            messageDomainService.joinRoom(roomA.id, userId = 8003L)
            messageDomainService.joinRoom(roomA.id, userId = 8004L)
            messageDomainService.sendMessage(roomA.id, 8004L, "A 방 안읽은 메시지")

            val roomB = messageDomainService.createGroupRoom("읽은 방 B", emptyList())
            messageDomainService.joinRoom(roomB.id, userId = 8003L)

            When("GET /rooms/me/unread 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/rooms/me/unread",
                    HttpMethod.GET,
                    HttpEntity<Void>(authHeaders(8003L)),
                    String::class.java,
                )

                Then("200 OK 와 함께 방별 안읽은 수 목록이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val unreadList: List<RoomUnreadResponse> = objectMapper.readValue(
                        response.body,
                        object : TypeReference<List<RoomUnreadResponse>>() {},
                    )
                    unreadList.size shouldBe 2
                    val roomAUnread = unreadList.first { it.roomId == roomA.id }
                    roomAUnread.unreadCount shouldBe 1L
                }
            }
        }

        Given("방에 참여하지 않은 사용자") {
            val room = messageDomainService.createGroupRoom("비참여자 테스트 방", emptyList())
            messageDomainService.joinRoom(room.id, userId = 8005L)

            When("비참여자가 POST /rooms/{roomId}/read 를 호출하면") {
                val body = objectMapper.writeValueAsString(mapOf("lastReadMessageId" to 1L))
                val response = restTemplate.exchange(
                    "${baseUrl()}/rooms/${room.id}/read",
                    HttpMethod.POST,
                    HttpEntity(body, authHeaders(9999L)),
                    String::class.java,
                )

                Then("403 Forbidden 이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }
    }
}
