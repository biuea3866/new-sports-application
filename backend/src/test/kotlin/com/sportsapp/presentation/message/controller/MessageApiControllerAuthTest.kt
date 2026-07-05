package com.sportsapp.presentation.message.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.presentation.message.dto.response.RoomResponse
import com.sportsapp.presentation.user.dto.response.LoginResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// BE-12: `/rooms/{roomId}/messages` 도 `/rooms` 하위 승격 범위에 포함되어 authenticated() 이고,
// 컨트롤러가 X-User-Id 헤더 대신 principal.id 를 사용하는지 검증한다.
@AutoConfigureMockMvc
class MessageApiControllerAuthTest(
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
        Given("Bearer JWT 없이 메시지를 전송·조회할 때") {
            When("POST /rooms/{roomId}/messages 를 인증 헤더 없이 호출하면") {
                Then("401 이 반환된다") {
                    mockMvc.perform(
                        post("/rooms/1/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mapOf("content" to "안녕"))),
                    ).andExpect(status().isUnauthorized)
                }
            }

            When("GET /rooms/{roomId}/messages 를 인증 헤더 없이 호출하면") {
                Then("401 이 반환된다") {
                    mockMvc.perform(get("/rooms/1/messages")).andExpect(status().isUnauthorized)
                }
            }
        }

        Given("JWT로 인증된 두 사용자가 참여한 방이 있을 때") {
            val emailF = "message-auth-f@example.com"
            val emailG = "message-auth-g@example.com"
            val userIdF = registerUser(emailF)
            val userIdG = registerUser(emailG)
            val tokenF = login(emailF)
            val room = createDirectRoom(tokenF, listOf(userIdF, userIdG))

            When("F가 자신의 토큰으로 메시지를 전송하면") {
                Then("principal.id 기반으로 201 이 반환된다") {
                    mockMvc.perform(
                        post("/rooms/${room.id}/messages")
                            .header("Authorization", "Bearer $tokenF")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mapOf("content" to "안녕하세요"))),
                    ).andExpect(status().isCreated)
                }
            }

            When("F가 자신의 토큰으로 메시지 목록을 조회하면") {
                Then("principal.id 기반으로 200 이 반환된다") {
                    mockMvc.perform(
                        get("/rooms/${room.id}/messages").header("Authorization", "Bearer $tokenF"),
                    ).andExpect(status().isOk)
                }
            }
        }
    }
}
