package com.sportsapp.scenario.message

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.infrastructure.message.mysql.RoomJpaRepository
import com.sportsapp.infrastructure.message.mysql.RoomParticipantJpaRepository
import com.sportsapp.presentation.user.dto.response.LoginResponse
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * POST /rooms/{roomId}/guests/{userId}/evict 시나리오 (TDD FR-15, `EvictGuestUseCase`).
 *
 * `/rooms` 하위 경로는 `RoomApiControllerAuthTest`/`MessageApiControllerAuthTest` 선례대로
 * Authorization: Bearer JWT 로 인증한다 — X-User-Id 헤더는 클라이언트가 조작 가능해
 * 방장 검증을 우회할 수 있으므로 사용하지 않는다.
 */
@AutoConfigureMockMvc
class GuestEvictionApiScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val roomJpaRepository: RoomJpaRepository,
    @Autowired private val roomParticipantJpaRepository: RoomParticipantJpaRepository,
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
        Given("방장(MEMBER)과 게스트가 참여한 방") {
            val hostEmail = "guest-eviction-host@example.com"
            val hostUserId = registerUser(hostEmail)
            val hostToken = login(hostEmail)

            val room = roomJpaRepository.save(Room.createGroup("방장 수동 방출 시나리오", hostUserId = hostUserId))
            roomParticipantJpaRepository.save(RoomParticipant.create(room, hostUserId))
            val guest = roomParticipantJpaRepository.save(
                RoomParticipant.forGuest(room = room, userId = 5001L, canSpeak = true, expiresInDays = 7L),
            )

            When("방장이 자신의 JWT로 POST /rooms/{roomId}/guests/{userId}/evict 를 호출하면") {
                val response = mockMvc.perform(
                    post("/rooms/${room.id}/guests/${guest.userId}/evict")
                        .header("Authorization", "Bearer $hostToken")
                        .accept(MediaType.APPLICATION_JSON),
                )

                Then("204 No Content 가 반환된다") {
                    response.andExpect(status().isNoContent)
                }

                Then("게스트는 soft-delete 되어 방출된다") {
                    val evicted = roomParticipantJpaRepository.findAll().first { it.id == guest.id }
                    evicted.isDeleted shouldBe true
                }
            }
        }

        Given("방장이 아닌 게스트가 다른 게스트를 방출하려는 방") {
            val otherEmail = "guest-eviction-other@example.com"
            val otherUserId = registerUser(otherEmail)
            val otherToken = login(otherEmail)

            val room = roomJpaRepository.save(Room.createGroup("방장 검증 시나리오"))
            roomParticipantJpaRepository.save(
                RoomParticipant.forGuest(room = room, userId = otherUserId, canSpeak = true, expiresInDays = 7L),
            )
            val guest = roomParticipantJpaRepository.save(
                RoomParticipant.forGuest(room = room, userId = 5002L, canSpeak = true, expiresInDays = 7L),
            )

            When("방장이 아닌 사용자가 자신의 JWT로 방출을 요청하면") {
                val response = mockMvc.perform(
                    post("/rooms/${room.id}/guests/${guest.userId}/evict")
                        .header("Authorization", "Bearer $otherToken")
                        .accept(MediaType.APPLICATION_JSON),
                )

                Then("403 Forbidden 이 반환된다") {
                    response.andExpect(status().isForbidden)
                }
            }
        }

        Given("방출 대상이 방 참여자가 아닌 경우") {
            val hostEmail = "guest-eviction-host-2@example.com"
            val hostUserId = registerUser(hostEmail)
            val hostToken = login(hostEmail)

            val room = roomJpaRepository.save(Room.createGroup("비참여자 방출 시나리오", hostUserId = hostUserId))
            roomParticipantJpaRepository.save(RoomParticipant.create(room, hostUserId))

            When("존재하지 않는 참여자를 방출하려 하면") {
                val response = mockMvc.perform(
                    post("/rooms/${room.id}/guests/999999/evict")
                        .header("Authorization", "Bearer $hostToken")
                        .accept(MediaType.APPLICATION_JSON),
                )

                Then("403 Forbidden 이 반환된다") {
                    response.andExpect(status().isForbidden)
                }
            }
        }
    }
}
