package com.sportsapp.scenario.message

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.infrastructure.message.mysql.RoomJpaRepository
import com.sportsapp.infrastructure.message.mysql.RoomParticipantJpaRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** POST /rooms/{roomId}/guests/{userId}/evict 시나리오 (TDD FR-15, `EvictGuestUseCase`). */
@AutoConfigureMockMvc
class GuestEvictionApiScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val roomJpaRepository: RoomJpaRepository,
    @Autowired private val roomParticipantJpaRepository: RoomParticipantJpaRepository,
) : BaseIntegrationTest() {

    init {
        Given("[S-01] 방장(MEMBER)과 게스트가 참여한 방") {
            val room = roomJpaRepository.save(Room.createGroup("방장 수동 방출 시나리오"))
            val host = roomParticipantJpaRepository.save(RoomParticipant.create(room, 1L))
            val guest = roomParticipantJpaRepository.save(
                RoomParticipant.forGuest(room = room, userId = 5L, canSpeak = true, expiresInDays = 7L),
            )

            When("방장이 POST /rooms/{roomId}/guests/{userId}/evict 를 호출하면") {
                val response = mockMvc.perform(
                    post("/rooms/${room.id}/guests/${guest.userId}/evict")
                        .header("X-User-Id", host.userId)
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

        Given("[S-02] 방장이 아닌 게스트가 다른 게스트를 방출하려는 방") {
            val room = roomJpaRepository.save(Room.createGroup("방장 검증 시나리오"))
            val otherGuest = roomParticipantJpaRepository.save(
                RoomParticipant.forGuest(room = room, userId = 2L, canSpeak = true, expiresInDays = 7L),
            )
            val guest = roomParticipantJpaRepository.save(
                RoomParticipant.forGuest(room = room, userId = 5L, canSpeak = true, expiresInDays = 7L),
            )

            When("방장이 아닌 사용자가 방출을 요청하면") {
                val response = mockMvc.perform(
                    post("/rooms/${room.id}/guests/${guest.userId}/evict")
                        .header("X-User-Id", otherGuest.userId)
                        .accept(MediaType.APPLICATION_JSON),
                )

                Then("403 Forbidden 이 반환된다") {
                    response.andExpect(status().isForbidden)
                }
            }
        }

        Given("[S-03] 방출 대상이 방 참여자가 아닌 경우") {
            val room = roomJpaRepository.save(Room.createGroup("비참여자 방출 시나리오"))
            val host = roomParticipantJpaRepository.save(RoomParticipant.create(room, 1L))

            When("존재하지 않는 참여자(userId=999)를 방출하려 하면") {
                val response = mockMvc.perform(
                    post("/rooms/${room.id}/guests/999/evict")
                        .header("X-User-Id", host.userId)
                        .accept(MediaType.APPLICATION_JSON),
                )

                Then("403 Forbidden 이 반환된다") {
                    response.andExpect(status().isForbidden)
                }
            }
        }
    }
}
