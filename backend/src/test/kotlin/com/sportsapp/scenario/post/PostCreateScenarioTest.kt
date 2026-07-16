package com.sportsapp.scenario.post

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.user.gateway.JwtIssuer
import com.sportsapp.infrastructure.post.mysql.PostJpaRepository
import com.sportsapp.presentation.support.bearerTokenFor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** AUTH-04 — `X-User-Id` 헤더 대신 `Authorization: Bearer JWT`로 본인 식별한다. */
@AutoConfigureMockMvc
class PostCreateScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val postJpaRepository: PostJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val jwtIssuer: JwtIssuer,
) : BaseJpaIntegrationTest() {

    init {
        beforeEach {
            jdbcTemplate.execute("DELETE FROM comments")
            jdbcTemplate.execute("DELETE FROM posts")
        }

        Given("[S-01] 인증된 사용자(JWT userId=1)가 유효한 제목과 본문으로 POST /posts 요청 시") {
            When("[S-01] 201 Created가 반환되고 목록 조회 시 노출된다") {
                Then("[S-01] 작성 후 GET /posts 에서 1건이 조회된다") {
                    mockMvc.perform(
                        post("/posts")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"title": "테스트 제목", "content": "테스트 본문 내용"}""")
                    ).andExpect(status().isCreated)
                        .andExpect(jsonPath("$.title").value("테스트 제목"))
                        .andExpect(jsonPath("$.userId").value(1))

                    mockMvc.perform(
                        get("/posts")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.content[0].title").value("테스트 제목"))
                }
            }
        }

        Given("[S-02] 빈 제목으로 POST /posts 요청 시") {
            When("[S-02] 빈 title을 전달하면") {
                Then("[S-02] 4xx 오류가 반환된다") {
                    mockMvc.perform(
                        post("/posts")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"title": "", "content": "본문 내용"}""")
                    ).andExpect(status().is4xxClientError)
                }
            }
        }

        Given("[S-03] 빈 본문으로 POST /posts 요청 시") {
            When("[S-03] 빈 content를 전달하면") {
                Then("[S-03] 4xx 오류가 반환된다") {
                    mockMvc.perform(
                        post("/posts")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"title": "제목", "content": ""}""")
                    ).andExpect(status().is4xxClientError)
                }
            }
        }

        // AUTH-04: 이전에는 X-User-Id 헤더 누락 시 컨트롤러 파라미터 바인딩 단계에서 400을 반환했다.
        // 지금은 /posts/** 비-GET 경로가 SecurityConfig 상 authenticated()라, JWT가 없으면 컨트롤러에
        // 도달하기 전에 Spring Security가 401을 반환한다(인가 계층에서 먼저 차단 — 더 안전한 위치로 이동).
        Given("[S-04] JWT 없이 POST /posts 요청 시") {
            When("[S-04] Authorization 헤더를 포함하지 않으면") {
                Then("[S-04] 401 Unauthorized가 반환된다") {
                    mockMvc.perform(
                        post("/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"title": "제목", "content": "본문 내용"}""")
                    ).andExpect(status().isUnauthorized)
                }
            }
        }

        Given("[S-05] 여러 게시글 작성 후 목록 조회 시") {
            When("[S-05] 3건을 순서대로 작성하면") {
                Then("[S-05] createdAt 내림차순으로 3건이 반환된다") {
                    repeat(3) { index ->
                        mockMvc.perform(
                            post("/posts")
                                .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"title": "글 ${index + 1}", "content": "내용 ${index + 1}"}""")
                        ).andExpect(status().isCreated)
                    }

                    mockMvc.perform(
                        get("/posts")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(3))
                }
            }
        }
    }
}
