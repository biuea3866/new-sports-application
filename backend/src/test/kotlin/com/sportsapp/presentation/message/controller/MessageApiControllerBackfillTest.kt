package com.sportsapp.presentation.message.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.message.entity.Message
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.vo.ParticipantType
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.infrastructure.message.mysql.MessageJpaRepository
import com.sportsapp.infrastructure.message.mysql.RoomJpaRepository
import com.sportsapp.infrastructure.message.mysql.RoomParticipantJpaRepository
import com.sportsapp.presentation.message.dto.response.MessageResponse
import com.sportsapp.presentation.user.dto.response.LoginResponse
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * GET /rooms/{roomId}/messages/backfill 시나리오 (TDD FR-10, 재연결 backfill).
 *
 * `/rooms` 하위 경로는 `MessageApiControllerAuthTest` 선례대로 Authorization: Bearer JWT 로
 * 인증한다 (principal.id 기반, X-User-Id 헤더 미사용).
 */
@AutoConfigureMockMvc
class MessageApiControllerBackfillTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val roomJpaRepository: RoomJpaRepository,
    @Autowired private val roomParticipantJpaRepository: RoomParticipantJpaRepository,
    @Autowired private val messageJpaRepository: MessageJpaRepository,
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val objectMapper: ObjectMapper,
) : BaseJpaIntegrationTest() {

    private fun registerUser(email: String, password: String = "Password1!"): Long =
        userDomainService.register(email, password).id

    private fun login(email: String, password: String = "Password1!"): String {
        val body = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readValue(body, LoginResponse::class.java).accessToken
    }

    init {
        Given("방에 참여 중인 사용자와 끊긴 구간에 쌓인 메시지 10건") {
            val email = "backfill-participant@example.com"
            val userId = registerUser(email)
            val token = login(email)

            val room = roomJpaRepository.save(Room.createDirect())
            roomParticipantJpaRepository.save(RoomParticipant.create(room, userId))
            val cursorMessage = messageJpaRepository.save(Message.create(room, userId, "커서 지점"))
            val afterMessages = (1..10).map { index ->
                messageJpaRepository.save(Message.create(room, userId, "끊긴 구간 메시지$index"))
            }

            When("GET /rooms/{roomId}/messages/backfill?afterMessageId=cursor 를 호출하면") {
                val body = mockMvc.perform(
                    get("/rooms/${room.id}/messages/backfill")
                        .header("Authorization", "Bearer $token")
                        .param("afterMessageId", cursorMessage.id.toString()),
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val result: List<MessageResponse> = objectMapper.readValue(
                    body,
                    objectMapper.typeFactory.constructCollectionType(List::class.java, MessageResponse::class.java),
                )

                Then("id > afterMessageId 인 메시지 10건이 오름차순으로 반환된다") {
                    result shouldHaveSize 10
                    result.map { it.id } shouldBe afterMessages.map { it.id }
                }
            }

            When("동일한 afterMessageId 로 재요청하면") {
                val firstBody = mockMvc.perform(
                    get("/rooms/${room.id}/messages/backfill")
                        .header("Authorization", "Bearer $token")
                        .param("afterMessageId", cursorMessage.id.toString()),
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val secondBody = mockMvc.perform(
                    get("/rooms/${room.id}/messages/backfill")
                        .header("Authorization", "Bearer $token")
                        .param("afterMessageId", cursorMessage.id.toString()),
                ).andExpect(status().isOk).andReturn().response.contentAsString

                Then("동일한 결과를 반환한다 (멱등)") {
                    firstBody shouldBe secondBody
                }
            }
        }

        Given("최신까지 읽어 끊긴 구간이 없는 사용자") {
            val email = "backfill-uptodate@example.com"
            val userId = registerUser(email)
            val token = login(email)

            val room = roomJpaRepository.save(Room.createDirect())
            roomParticipantJpaRepository.save(RoomParticipant.create(room, userId))
            val lastMessage = messageJpaRepository.save(Message.create(room, userId, "마지막 메시지"))

            When("GET backfill?afterMessageId=lastMessage.id 를 호출하면") {
                val body = mockMvc.perform(
                    get("/rooms/${room.id}/messages/backfill")
                        .header("Authorization", "Bearer $token")
                        .param("afterMessageId", lastMessage.id.toString()),
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val result: List<MessageResponse> = objectMapper.readValue(
                    body,
                    objectMapper.typeFactory.constructCollectionType(List::class.java, MessageResponse::class.java),
                )

                Then("빈 목록을 반환한다") {
                    result.shouldBeEmpty()
                }
            }
        }

        Given("방 참여자가 아닌 사용자") {
            val hostEmail = "backfill-host@example.com"
            val hostUserId = registerUser(hostEmail)

            val outsiderEmail = "backfill-outsider@example.com"
            registerUser(outsiderEmail)
            val outsiderToken = login(outsiderEmail)

            val room = roomJpaRepository.save(Room.createDirect())
            roomParticipantJpaRepository.save(RoomParticipant.create(room, hostUserId))
            val cursorMessage = messageJpaRepository.save(Message.create(room, hostUserId, "커서 지점"))

            When("비참여자가 GET backfill 을 호출하면") {
                val response = mockMvc.perform(
                    get("/rooms/${room.id}/messages/backfill")
                        .header("Authorization", "Bearer $outsiderToken")
                        .param("afterMessageId", cursorMessage.id.toString()),
                )

                Then("403 Forbidden 이 반환된다") {
                    response.andExpect(status().isForbidden)
                }
            }
        }

        Given("만료된 게스트 참여자") {
            val hostEmail = "backfill-guest-host@example.com"
            val hostUserId = registerUser(hostEmail)

            val guestEmail = "backfill-expired-guest@example.com"
            val guestUserId = registerUser(guestEmail)
            val guestToken = login(guestEmail)

            val room = roomJpaRepository.save(Room.createDirect())
            roomParticipantJpaRepository.save(RoomParticipant.create(room, hostUserId))
            roomParticipantJpaRepository.save(
                RoomParticipant.reconstitute(
                    room = room,
                    userId = guestUserId,
                    joinedAt = ZonedDateTime.now().minusDays(10),
                    participantType = ParticipantType.GUEST,
                    canSpeak = true,
                    expiresAt = ZonedDateTime.now().minusDays(1),
                    lastReadMessageId = null,
                ),
            )
            val cursorMessage = messageJpaRepository.save(Message.create(room, hostUserId, "커서 지점"))

            When("만료된 게스트가 GET backfill 을 호출하면") {
                val response = mockMvc.perform(
                    get("/rooms/${room.id}/messages/backfill")
                        .header("Authorization", "Bearer $guestToken")
                        .param("afterMessageId", cursorMessage.id.toString()),
                )

                Then("403 Forbidden 이 반환된다") {
                    response.andExpect(status().isForbidden)
                }
            }
        }

        Given("Bearer JWT 없이 backfill 을 요청할 때") {
            val room = roomJpaRepository.save(Room.createDirect())

            When("GET /rooms/{roomId}/messages/backfill 를 인증 헤더 없이 호출하면") {
                Then("401 이 반환된다") {
                    mockMvc.perform(
                        get("/rooms/${room.id}/messages/backfill").param("afterMessageId", "0"),
                    ).andExpect(status().isUnauthorized)
                }
            }
        }
    }
}
