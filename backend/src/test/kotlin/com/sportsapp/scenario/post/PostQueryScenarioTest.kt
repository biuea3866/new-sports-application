package com.sportsapp.scenario.post

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.post.Comment
import com.sportsapp.domain.post.Post
import com.sportsapp.domain.post.PostType
import com.sportsapp.infrastructure.persistence.post.CommentJpaRepository
import com.sportsapp.infrastructure.persistence.post.PostJpaRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class PostQueryScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val postJpaRepository: PostJpaRepository,
    @Autowired private val commentJpaRepository: CommentJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        beforeEach {
            jdbcTemplate.execute("DELETE FROM comments")
            jdbcTemplate.execute("DELETE FROM posts")
        }

        Given("[S-01] FREE 타입 풋살 관련 Post 3건, 무관한 Post 2건이 저장된 상태에서") {
            repeat(3) { i ->
                postJpaRepository.save(
                    Post.create(userId = 1L, title = "풋살 모집 $i", content = "같이 해요", type = PostType.FREE)
                )
            }
            repeat(2) { i ->
                postJpaRepository.save(
                    Post.create(userId = 2L, title = "농구 모집 $i", content = "농구합시다", type = PostType.NOTICE)
                )
            }

            When("[S-01] GET /posts?type=FREE&keyword=풋살&page=0&size=10 요청 시") {
                val response = mockMvc.perform(
                    get("/posts")
                        .param("type", "FREE")
                        .param("keyword", "풋살")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("200 OK와 FREE 타입 풋살 관련 3건이 반환된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(3))
                        .andExpect(jsonPath("$.content[0].type").value("FREE"))
                }
            }
        }

        Given("[S-02] Post 1건과 댓글 3건이 저장된 상태에서") {
            val post = postJpaRepository.save(
                Post.create(userId = 1L, title = "게시글 제목", content = "게시글 내용", type = PostType.FREE)
            )
            repeat(3) { i ->
                commentJpaRepository.save(Comment.create(post = post, userId = 1L, content = "댓글 $i"))
            }

            When("[S-02] GET /posts/{id} 단건 조회 시") {
                val response = mockMvc.perform(
                    get("/posts/${post.id}")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("200 OK와 댓글 3건이 포함된 응답이 반환된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.id").value(post.id))
                        .andExpect(jsonPath("$.comments.length()").value(3))
                }
            }
        }

        Given("[S-03] Post 2건이 저장된 상태에서") {
            postJpaRepository.save(Post.create(userId = 1L, title = "글 1", content = "내용", type = PostType.FREE))
            postJpaRepository.save(Post.create(userId = 1L, title = "글 2", content = "내용", type = PostType.FREE))

            When("[S-03] 인증 없이 GET /posts 요청 시") {
                val response = mockMvc.perform(
                    get("/posts")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("인증 없이도 200 OK가 반환된다") {
                    response.andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(2))
                }
            }
        }

        Given("존재하지 않는 postId로 단건 조회할 때") {
            When("GET /posts/99999 요청 시") {
                val response = mockMvc.perform(
                    get("/posts/99999")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("404 Not Found가 반환된다") {
                    response.andExpect(status().isNotFound)
                }
            }
        }

        Given("[S-01] userId 필터로 조회할 때") {
            postJpaRepository.save(Post.create(userId = 10L, title = "사용자10 글1", content = "내용", type = PostType.FREE))
            postJpaRepository.save(Post.create(userId = 10L, title = "사용자10 글2", content = "내용", type = PostType.FREE))
            postJpaRepository.save(Post.create(userId = 20L, title = "사용자20 글1", content = "내용", type = PostType.NOTICE))

            When("GET /posts?userId=10 요청 시") {
                val response = mockMvc.perform(
                    get("/posts")
                        .param("userId", "10")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("userId=10의 Post 2건만 반환된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(2))
                }
            }
        }
    }
}
