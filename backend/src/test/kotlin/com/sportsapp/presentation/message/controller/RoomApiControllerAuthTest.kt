package com.sportsapp.presentation.message.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.presentation.message.dto.response.RoomResponse
import com.sportsapp.presentation.user.dto.response.LoginResponse
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// BE-12: `/rooms` 하위 경로 전체가 permitAll -> authenticated() 로 승격되고, 컨트롤러가
// X-User-Id 헤더 대신 Authorization: Bearer JWT(@AuthenticationPrincipal) 를 사용하는지 검증한다.
@AutoConfigureMockMvc
class RoomApiControllerAuthTest(
    @Autowired private val mockMvc: MockMvc,
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

    private fun createDirectRoom(token: String, participantIds: List<Long>): RoomResponse {
        val body = mockMvc.perform(
            post("/rooms")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("participantIds" to participantIds, "name" to null))),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return objectMapper.readValue(body, RoomResponse::class.java)
    }

    init {
        Given("Bearer JWT 없이 방목록을 조회할 때") {
            When("GET /rooms/me 를 인증 헤더 없이 호출하면") {
                Then("401 이 반환된다 (authenticated 승격)") {
                    mockMvc.perform(get("/rooms/me")).andExpect(status().isUnauthorized)
                }
            }
        }

        Given("Bearer JWT 없이 방을 생성·조회·삭제할 때") {
            When("POST /rooms 를 인증 헤더 없이 호출하면") {
                Then("401 이 반환된다") {
                    mockMvc.perform(
                        post("/rooms")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mapOf("participantIds" to listOf(1L, 2L), "name" to null))),
                    ).andExpect(status().isUnauthorized)
                }
            }
        }

        Given("JWT로 인증된 두 사용자 A·B 가 1:1 방을 생성한 상태") {
            val emailA = "room-auth-a@example.com"
            val emailB = "room-auth-b@example.com"
            val userIdA = registerUser(emailA)
            val userIdB = registerUser(emailB)
            val tokenA = login(emailA)
            val tokenB = login(emailB)
            val room = createDirectRoom(tokenA, listOf(userIdA, userIdB))

            When("A가 GET /rooms/{id} 를 자신의 토큰으로 호출하면") {
                Then("principal.id(A) 기반으로 정상 200 을 반환한다") {
                    mockMvc.perform(
                        get("/rooms/${room.id}").header("Authorization", "Bearer $tokenA"),
                    ).andExpect(status().isOk)
                }
            }

            When("B가 GET /rooms/{id} 를 자신의 토큰으로 호출하면") {
                Then("participant 인 B 도 principal.id 기반으로 200 을 반환한다") {
                    mockMvc.perform(
                        get("/rooms/${room.id}").header("Authorization", "Bearer $tokenB"),
                    ).andExpect(status().isOk)
                }
            }

            When("A가 DELETE /rooms/{id} 로 방을 나가면") {
                Then("204 가 반환된다") {
                    mockMvc.perform(
                        delete("/rooms/${room.id}").header("Authorization", "Bearer $tokenA"),
                    ).andExpect(status().isNoContent)
                }
            }
        }

        Given("A가 메시지를 보낸 방과, 메시지가 없는 방을 각각 가진 상태") {
            val emailC = "room-auth-c@example.com"
            val emailD = "room-auth-d@example.com"
            val emailE = "room-auth-e@example.com"
            val userIdC = registerUser(emailC)
            val userIdD = registerUser(emailD)
            val userIdE = registerUser(emailE)
            val tokenC = login(emailC)

            val roomWithMessage = createDirectRoom(tokenC, listOf(userIdC, userIdD))
            val longContent = "안".repeat(80)
            mockMvc.perform(
                post("/rooms/${roomWithMessage.id}/messages")
                    .header("Authorization", "Bearer $tokenC")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("content" to longContent))),
            ).andExpect(status().isCreated)

            val roomWithoutMessage = createDirectRoom(tokenC, listOf(userIdC, userIdE))

            When("C가 GET /rooms/me 를 자신의 토큰으로 호출하면") {
                val listBody = mockMvc.perform(
                    get("/rooms/me").header("Authorization", "Bearer $tokenC"),
                ).andExpect(status().isOk).andReturn().response.getContentAsString(Charsets.UTF_8)
                val rooms = objectMapper.readValue(listBody, Array<RoomResponse>::class.java).toList()

                Then("본인이 참여한 두 방만 반환되고, 메시지 있는 방은 lastMessagePreview(50자)·lastMessageAt 을 포함한다") {
                    rooms.map { it.id } shouldContain roomWithMessage.id
                    rooms.map { it.id } shouldContain roomWithoutMessage.id

                    val withMessage = rooms.first { it.id == roomWithMessage.id }
                    withMessage.lastMessagePreview?.length shouldBe 50
                    withMessage.lastMessageAt.shouldNotBeNull()
                    withMessage.contextType.shouldBeNull()
                }

                Then("메시지가 없는 방은 lastMessagePreview·lastMessageAt 이 null 이다") {
                    val withoutMessage = rooms.first { it.id == roomWithoutMessage.id }
                    withoutMessage.lastMessagePreview.shouldBeNull()
                    withoutMessage.lastMessageAt.shouldBeNull()
                }
            }
        }
    }
}
