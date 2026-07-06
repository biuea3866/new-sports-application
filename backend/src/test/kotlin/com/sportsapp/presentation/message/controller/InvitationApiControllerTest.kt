package com.sportsapp.presentation.message.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.service.MessageDomainService
import com.sportsapp.domain.user.service.UserDomainService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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

class InvitationApiControllerTest(
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val messageDomainService: MessageDomainService,
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
            "게스트 초대 테스트방 ${System.nanoTime()}",
            listOf(hostUserId),
            hostUserId = hostUserId,
        )

    private fun inviteRequestBody(inviteeUserId: Long, canSpeak: Boolean = true, expiresInDays: Long = 7L): String =
        objectMapper.writeValueAsString(
            mapOf("inviteeUserId" to inviteeUserId, "canSpeak" to canSpeak, "expiresInDays" to expiresInDays),
        )

    init {
        Given("방장이 비멤버 정회원을 초대하는 상황") {
            val (hostId, hostToken) = registerAndLogin("invite-host-1@example.com")
            val (guestId, _) = registerAndLogin("invite-guest-1@example.com")
            val room = createGroupRoomHostedBy(hostId)

            When("POST /rooms/{roomId}/invitations 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/rooms/${room.id}/invitations",
                    HttpMethod.POST,
                    HttpEntity(inviteRequestBody(guestId), authHeaders(hostToken)),
                    String::class.java,
                )

                Then("201 응답과 PENDING 초대가 reused=false 로 반환된다") {
                    response.statusCode shouldBe HttpStatus.CREATED
                    response.body shouldContain "PENDING"
                    response.body shouldContain "\"inviteeUserId\":$guestId"
                    response.body shouldContain "\"reused\":false"
                }
            }
        }

        Given("동일 (room, invitee) 로 PENDING 상태에서 재초대하는 상황") {
            val (hostId, hostToken) = registerAndLogin("invite-host-2@example.com")
            val (guestId, _) = registerAndLogin("invite-guest-2@example.com")
            val room = createGroupRoomHostedBy(hostId)
            val firstResponse = restTemplate.exchange(
                "${baseUrl()}/rooms/${room.id}/invitations",
                HttpMethod.POST,
                HttpEntity(inviteRequestBody(guestId), authHeaders(hostToken)),
                String::class.java,
            )
            val firstInvitationId = objectMapper.readTree(firstResponse.body).get("id").asLong()

            When("동일 조건으로 다시 POST 하면") {
                val secondResponse = restTemplate.exchange(
                    "${baseUrl()}/rooms/${room.id}/invitations",
                    HttpMethod.POST,
                    HttpEntity(inviteRequestBody(guestId), authHeaders(hostToken)),
                    String::class.java,
                )

                Then("기존 초대와 동일한 id 가 200 OK·reused=true 로 반환된다 (멱등)") {
                    secondResponse.statusCode shouldBe HttpStatus.OK
                    objectMapper.readTree(secondResponse.body).get("id").asLong() shouldBe firstInvitationId
                    objectMapper.readTree(secondResponse.body).get("reused").asBoolean() shouldBe true
                }
            }
        }

        Given("초대 수락 상황") {
            val (hostId, hostToken) = registerAndLogin("invite-host-3@example.com")
            val (guestId, guestToken) = registerAndLogin("invite-guest-3@example.com")
            val room = createGroupRoomHostedBy(hostId)
            val inviteResponse = restTemplate.exchange(
                "${baseUrl()}/rooms/${room.id}/invitations",
                HttpMethod.POST,
                HttpEntity(inviteRequestBody(guestId, canSpeak = true, expiresInDays = 7L), authHeaders(hostToken)),
                String::class.java,
            )
            val invitationId = objectMapper.readTree(inviteResponse.body).get("id").asLong()

            When("초대 대상이 POST /rooms/invitations/{id}/accept 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/rooms/invitations/$invitationId/accept",
                    HttpMethod.POST,
                    HttpEntity<Void>(authHeaders(guestToken)),
                    String::class.java,
                )

                Then("200 응답과 ACCEPTED 상태가 반환되고 게스트 참여자가 추가된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldContain "ACCEPTED"
                    val participantCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM room_participants WHERE room_id = ? AND user_id = ? AND deleted_at IS NULL",
                        Int::class.java,
                        room.id,
                        guestId,
                    )
                    participantCount shouldBe 1
                }
            }
        }

        Given("초대 거절 상황") {
            val (hostId, hostToken) = registerAndLogin("invite-host-4@example.com")
            val (guestId, guestToken) = registerAndLogin("invite-guest-4@example.com")
            val room = createGroupRoomHostedBy(hostId)
            val inviteResponse = restTemplate.exchange(
                "${baseUrl()}/rooms/${room.id}/invitations",
                HttpMethod.POST,
                HttpEntity(inviteRequestBody(guestId), authHeaders(hostToken)),
                String::class.java,
            )
            val invitationId = objectMapper.readTree(inviteResponse.body).get("id").asLong()

            When("초대 대상이 POST /rooms/invitations/{id}/reject 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/rooms/invitations/$invitationId/reject",
                    HttpMethod.POST,
                    HttpEntity<Void>(authHeaders(guestToken)),
                    String::class.java,
                )

                Then("200 응답과 REJECTED 상태가 반환되고 참여자는 추가되지 않는다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldContain "REJECTED"
                    val participantCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM room_participants WHERE room_id = ? AND user_id = ? AND deleted_at IS NULL",
                        Int::class.java,
                        room.id,
                        guestId,
                    )
                    participantCount shouldBe 0
                }
            }
        }

        Given("이미 ACCEPTED 된 초대를 다시 accept 하는 상황") {
            val (hostId, hostToken) = registerAndLogin("invite-host-5@example.com")
            val (guestId, guestToken) = registerAndLogin("invite-guest-5@example.com")
            val room = createGroupRoomHostedBy(hostId)
            val inviteResponse = restTemplate.exchange(
                "${baseUrl()}/rooms/${room.id}/invitations",
                HttpMethod.POST,
                HttpEntity(inviteRequestBody(guestId), authHeaders(hostToken)),
                String::class.java,
            )
            val invitationId = objectMapper.readTree(inviteResponse.body).get("id").asLong()
            restTemplate.exchange(
                "${baseUrl()}/rooms/invitations/$invitationId/accept",
                HttpMethod.POST,
                HttpEntity<Void>(authHeaders(guestToken)),
                String::class.java,
            )

            When("다시 accept 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/rooms/invitations/$invitationId/accept",
                    HttpMethod.POST,
                    HttpEntity<Void>(authHeaders(guestToken)),
                    String::class.java,
                )

                Then("409 응답이 반환된다 (terminal 재전이 거부)") {
                    response.statusCode shouldBe HttpStatus.CONFLICT
                }
            }
        }

        Given("방장이 아닌 사용자가 초대를 시도하는 상황") {
            val (hostId, _) = registerAndLogin("invite-host-6@example.com")
            val (nonHostId, nonHostToken) = registerAndLogin("invite-nonhost-6@example.com")
            val (guestId, _) = registerAndLogin("invite-guest-6@example.com")
            val room = createGroupRoomHostedBy(hostId)

            When("방장이 아닌 사용자가 POST /rooms/{roomId}/invitations 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/rooms/${room.id}/invitations",
                    HttpMethod.POST,
                    HttpEntity(inviteRequestBody(guestId), authHeaders(nonHostToken)),
                    String::class.java,
                )

                Then("403 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }

        Given("초대 대상이 아닌 사용자가 accept 를 시도하는 상황") {
            val (hostId, hostToken) = registerAndLogin("invite-host-7@example.com")
            val (guestId, _) = registerAndLogin("invite-guest-7@example.com")
            val (strangerId, strangerToken) = registerAndLogin("invite-stranger-7@example.com")
            val room = createGroupRoomHostedBy(hostId)
            val inviteResponse = restTemplate.exchange(
                "${baseUrl()}/rooms/${room.id}/invitations",
                HttpMethod.POST,
                HttpEntity(inviteRequestBody(guestId), authHeaders(hostToken)),
                String::class.java,
            )
            val invitationId = objectMapper.readTree(inviteResponse.body).get("id").asLong()

            When("초대 대상이 아닌 사용자가 POST /rooms/invitations/{id}/accept 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/rooms/invitations/$invitationId/accept",
                    HttpMethod.POST,
                    HttpEntity<Void>(authHeaders(strangerToken)),
                    String::class.java,
                )

                Then("403 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }

        Given("초대 수신함 조회 상황 — PENDING 1건과 ACCEPTED 1건이 존재") {
            val (hostId, hostToken) = registerAndLogin("invite-host-8@example.com")
            val (guestId, guestToken) = registerAndLogin("invite-guest-8@example.com")
            val pendingRoom = createGroupRoomHostedBy(hostId)
            val acceptedRoom = createGroupRoomHostedBy(hostId)

            val pendingInviteResponse = restTemplate.exchange(
                "${baseUrl()}/rooms/${pendingRoom.id}/invitations",
                HttpMethod.POST,
                HttpEntity(inviteRequestBody(guestId), authHeaders(hostToken)),
                String::class.java,
            )
            val pendingInvitationId = objectMapper.readTree(pendingInviteResponse.body).get("id").asLong()

            val acceptedInviteResponse = restTemplate.exchange(
                "${baseUrl()}/rooms/${acceptedRoom.id}/invitations",
                HttpMethod.POST,
                HttpEntity(inviteRequestBody(guestId), authHeaders(hostToken)),
                String::class.java,
            )
            val acceptedInvitationId = objectMapper.readTree(acceptedInviteResponse.body).get("id").asLong()
            restTemplate.exchange(
                "${baseUrl()}/rooms/invitations/$acceptedInvitationId/accept",
                HttpMethod.POST,
                HttpEntity<Void>(authHeaders(guestToken)),
                String::class.java,
            )

            When("GET /rooms/invitations/me 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/rooms/invitations/me",
                    HttpMethod.GET,
                    HttpEntity<Void>(authHeaders(guestToken)),
                    String::class.java,
                )

                Then("PENDING 초대만 반환되고 ACCEPTED 는 제외된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    val invitationIds = objectMapper.readTree(response.body).map { it.get("id").asLong() }
                    invitationIds shouldBe listOf(pendingInvitationId)
                }
            }
        }

        Given("인증 토큰 없이 초대 API 를 호출하는 상황") {
            val (hostId, _) = registerAndLogin("invite-host-9@example.com")
            val room = createGroupRoomHostedBy(hostId)

            When("Authorization 헤더 없이 GET /rooms/invitations/me 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/rooms/invitations/me",
                    HttpMethod.GET,
                    HttpEntity<Void>(HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
                    String::class.java,
                )

                Then("401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            When("Authorization 헤더 없이 POST /rooms/{roomId}/invitations 를 호출하면") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/rooms/${room.id}/invitations",
                    HttpMethod.POST,
                    HttpEntity(
                        inviteRequestBody(999L),
                        HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
                    ),
                    String::class.java,
                )

                Then("401 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }
    }
}
