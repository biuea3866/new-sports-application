package com.sportsapp.scenario.post

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.vo.PostType
import com.sportsapp.infrastructure.post.mysql.CommentJpaRepository
import com.sportsapp.infrastructure.post.mysql.PostJpaRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class CommentApiScenarioTest(
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

        Given("[S-01] 정상 상태의 Post 가 저장된 상태에서") {
            When("[S-01] POST /posts/{postId}/comments 로 댓글 작성 시") {
                Then("[S-01] 201이 반환되고 GET /posts/{postId}/comments 로 즉시 조회된다") {
                    val savedPost = postJpaRepository.save(
                        Post.create(userId = 1L, title = "제목", content = "내용", type = PostType.FREE)
                    )

                    mockMvc.perform(
                        post("/posts/${savedPost.id}/comments")
                            .header("X-User-Id", 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"content": "새 댓글"}""")
                    ).andExpect(status().isCreated)

                    mockMvc.perform(
                        get("/posts/${savedPost.id}/comments")
                            .param("page", "0")
                            .param("size", "20")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.content[0].content").value("새 댓글"))
                }
            }
        }

        Given("[S-02] Post 와 댓글이 저장된 상태에서") {
            When("[S-02] Post 작성자(userId=1)가 타인 댓글 삭제 시도 시") {
                Then("[S-02] 403 Forbidden 이 반환된다") {
                    val savedPost = postJpaRepository.save(
                        Post.create(userId = 1L, title = "제목", content = "내용", type = PostType.FREE)
                    )
                    mockMvc.perform(
                        post("/posts/${savedPost.id}/comments")
                            .header("X-User-Id", 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"content": "댓글 내용"}""")
                    ).andExpect(status().isCreated)

                    val commentId = commentJpaRepository.findAll().first().id

                    mockMvc.perform(
                        delete("/comments/$commentId")
                            .header("X-User-Id", 1L)
                    ).andExpect(status().isForbidden)
                }
            }
        }

        Given("[S-01] POST → DELETE → GET 전체 플로우에서") {
            When("[S-01] GET /posts/{postId}/comments 조회 시") {
                Then("[S-01] 삭제된 댓글은 목록에서 제외되어 totalElements 가 0 이다") {
                    val savedPost = postJpaRepository.save(
                        Post.create(userId = 1L, title = "제목", content = "내용", type = PostType.FREE)
                    )
                    mockMvc.perform(
                        post("/posts/${savedPost.id}/comments")
                            .header("X-User-Id", 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"content": "삭제될 댓글"}""")
                    ).andExpect(status().isCreated)

                    val commentId = commentJpaRepository.findAll().first().id

                    mockMvc.perform(
                        delete("/comments/$commentId")
                            .header("X-User-Id", 10L)
                    ).andExpect(status().isNoContent)

                    mockMvc.perform(
                        get("/posts/${savedPost.id}/comments")
                            .param("page", "0")
                            .param("size", "20")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(0))
                }
            }
        }

        Given("[S-03] Post 와 댓글 5건이 저장된 상태에서") {
            When("[S-03] Post 소프트 삭제 후 댓글 목록 조회 시") {
                Then("[S-03] 댓글 목록은 여전히 반환된다 (listComments는 Post 삭제 여부를 검증하지 않음)") {
                    val savedPost = postJpaRepository.save(
                        Post.create(userId = 1L, title = "제목", content = "내용", type = PostType.FREE)
                    )
                    repeat(5) { index ->
                        mockMvc.perform(
                            post("/posts/${savedPost.id}/comments")
                                .header("X-User-Id", 10L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"content": "댓글 $index"}""")
                        ).andExpect(status().isCreated)
                    }

                    val postEntity = postJpaRepository.findById(savedPost.id).get()
                    postEntity.softDelete(1L)
                    postJpaRepository.save(postEntity)

                    mockMvc.perform(
                        get("/posts/${savedPost.id}/comments")
                            .param("page", "0")
                            .param("size", "20")
                            .accept(MediaType.APPLICATION_JSON)
                    ).andExpect(status().isOk)
                }
            }
        }

        Given("[R-01] 댓글 25건이 저장된 상태에서 page=0, size=20 으로 조회 시") {
            When("[R-01] page=0, size=20 으로 조회 시") {
                Then("[R-01] 20건이 반환되고 totalElements 는 25 이다") {
                    val savedPost = postJpaRepository.save(
                        Post.create(userId = 1L, title = "제목", content = "내용", type = PostType.FREE)
                    )
                    repeat(25) { index ->
                        mockMvc.perform(
                            post("/posts/${savedPost.id}/comments")
                                .header("X-User-Id", 10L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"content": "댓글 ${index + 1}"}""")
                        ).andExpect(status().isCreated)
                    }

                    mockMvc.perform(
                        get("/posts/${savedPost.id}/comments")
                            .param("page", "0")
                            .param("size", "20")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(25))
                        .andExpect(jsonPath("$.content.length()").value(20))
                }
            }
        }

        Given("[R-01] 댓글 25건이 저장된 상태에서 page=1, size=20 으로 조회 시") {
            When("[R-01] page=1, size=20 으로 조회 시") {
                Then("[R-01] 나머지 5건이 반환된다") {
                    val savedPost = postJpaRepository.save(
                        Post.create(userId = 1L, title = "제목", content = "내용", type = PostType.FREE)
                    )
                    repeat(25) { index ->
                        mockMvc.perform(
                            post("/posts/${savedPost.id}/comments")
                                .header("X-User-Id", 10L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"content": "댓글 ${index + 1}"}""")
                        ).andExpect(status().isCreated)
                    }

                    mockMvc.perform(
                        get("/posts/${savedPost.id}/comments")
                            .param("page", "1")
                            .param("size", "20")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.content.length()").value(5))
                }
            }
        }

        Given("[S-01] 미존재 Post 에 댓글 작성 시") {
            When("POST /posts/99999/comments 요청 시") {
                Then("[S-01] 404 Not Found 가 반환된다") {
                    mockMvc.perform(
                        post("/posts/99999/comments")
                            .header("X-User-Id", 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"content": "댓글"}""")
                    ).andExpect(status().isNotFound)
                }
            }
        }

        Given("[S-01] 삭제된 Post 에 댓글 작성 시") {
            When("POST /posts/{postId}/comments 요청 시") {
                Then("[S-01] 404 Not Found 가 반환된다") {
                    val savedPost = postJpaRepository.save(
                        Post.create(userId = 1L, title = "제목", content = "내용", type = PostType.FREE)
                    )
                    savedPost.softDelete(1L)
                    postJpaRepository.save(savedPost)

                    mockMvc.perform(
                        post("/posts/${savedPost.id}/comments")
                            .header("X-User-Id", 10L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"content": "댓글"}""")
                    ).andExpect(status().isNotFound)
                }
            }
        }
    }
}
