package com.sportsapp.presentation.message.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.service.GuestInvitationDomainService
import com.sportsapp.domain.message.service.MessageDomainService
import com.sportsapp.domain.message.vo.ParticipantType
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.presentation.message.dto.response.MyRoomParticipationResponse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

/**
 * GET /rooms/{roomId}/participation — "내 방 참여정보" 조회 (BE-14, FE-10 canSpeak/expiresAt
 * 하드코딩 degrade 해소). [InvitationApiControllerTest]와 동일한 RestTemplate 통합 스타일을 따른다.
 */
class RoomParticipationApiControllerTest(
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val messageDomainService: MessageDomainService,
    @Autowired private val guestInvitationDomainService: GuestInvitationDomainService,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val objectMapper: ObjectMapper,
    @LocalServerPort private val port: Int,
) : BaseIntegrationTest() {

    private val restTemplate = RestTemplate(
        HttpComponentsClientHttpRequestFactory(HttpClients.createDefault()),
    ).apply {
        errorHandler = object : ResponseErrorHandler {
            override fun hasError(response: ClientHttpResponse): Boolean = false
            override fun handleError(response: ClientHttpResponse) = Unit
        }
    }

    private fun baseUrl() = "http://localhost:$port"

    private fun registerAndLogin(email: String, password: String = "Pass1234!"): Pair<Long, String> {
        userDomainService.register(email, password)
        val userId = requireNotNull(
            jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long::class.java, email),
        )
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val response = restTemplate.exchange(
            "${baseUrl()}/auth/login",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java,
        )
        val accessToken = objectMapper.readTree(response.body).get("accessToken").asText()
        return userId to accessToken
    }

    private fun authHeaders(token: String): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        setBearerAuth(token)
    }

    private fun createGroupRoomHostedBy(hostUserId: Long): Room =
        messageDomainService.createGroupRoom(
            "참여정보 테스트방 ${System.nanoTime()}",
            listOf(hostUserId),
            hostUserId = hostUserId,
        )

    private fun getParticipation(roomId: Long, token: String) = restTemplate.exchange(
        "${baseUrl()}/rooms/$roomId/participation",
        HttpMethod.GET,
        HttpEntity<Void>(authHeaders(token)),
        String::class.java,
    )

    init {
        Given("방장 본인이 정회원으로 참여 중인 방") {
            val (hostId, hostToken) = registerAndLogin("participation-host-1@example.com")
            val room = createGroupRoomHostedBy(hostId)

            When("GET /rooms/{roomId}/participation 를 호출하면") {
                val response = getParticipation(room.id, hostToken)

                Then("200 응답과 함께 MEMBER·canSpeak=true·expiresAt=null·isHost=true 가 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readValue(response.body, MyRoomParticipationResponse::class.java)
                    body.roomId shouldBe room.id
                    body.participantType shouldBe ParticipantType.MEMBER
                    body.canSpeak shouldBe true
                    body.expiresAt.shouldBeNull()
                    body.isHost shouldBe true
                }
            }
        }

        Given("게스트로 초대를 수락해 발화 제한·만료 시각을 가진 참여자") {
            val (hostId, _) = registerAndLogin("participation-host-2@example.com")
            val (guestId, guestToken) = registerAndLogin("participation-guest-2@example.com")
            val room = createGroupRoomHostedBy(hostId)
            val invitationResult = guestInvitationDomainService.invite(
                roomId = room.id,
                inviterUserId = hostId,
                inviteeUserId = guestId,
                canSpeak = false,
                expiresInDays = 7L,
            )
            guestInvitationDomainService.accept(invitationId = invitationResult.invitation.id, userId = guestId)

            When("게스트 본인이 GET /rooms/{roomId}/participation 를 호출하면") {
                val response = getParticipation(room.id, guestToken)

                Then("200 응답과 함께 GUEST·canSpeak=false·expiresAt 채워짐·isHost=false 가 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readValue(response.body, MyRoomParticipationResponse::class.java)
                    body.participantType shouldBe ParticipantType.GUEST
                    body.canSpeak shouldBe false
                    body.expiresAt.shouldNotBeNull()
                    body.isHost shouldBe false
                }
            }
        }

        Given("방에 참여하지 않은 사용자") {
            val (hostId, _) = registerAndLogin("participation-host-3@example.com")
            val (_, strangerToken) = registerAndLogin("participation-stranger-3@example.com")
            val room = createGroupRoomHostedBy(hostId)

            When("GET /rooms/{roomId}/participation 를 호출하면") {
                val response = getParticipation(room.id, strangerToken)

                Then("403 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }

        Given("존재하지 않는 방") {
            val (_, hostToken) = registerAndLogin("participation-host-4@example.com")

            When("GET /rooms/999999/participation 를 호출하면") {
                val response = getParticipation(999999L, hostToken)

                Then("404 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        Given("인증 토큰 없이 참여정보 API 를 호출하는 상황") {
            val (hostId, _) = registerAndLogin("participation-host-5@example.com")
            val room = createGroupRoomHostedBy(hostId)

            When("Authorization 헤더 없이 GET /rooms/{roomId}/participation 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/rooms/${room.id}/participation",
                    HttpMethod.GET,
                    HttpEntity<Void>(HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
                    String::class.java,
                )

                Then("401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }
    }
}
