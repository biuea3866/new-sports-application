package com.sportsapp.infrastructure.persistence.post

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.repository.CommentRepository
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.repository.PostRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class CommentRepositoryTest(
    @Autowired private val commentRepository: CommentRepository,
    @Autowired private val postRepository: PostRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        beforeEach {
            jdbcTemplate.execute("DELETE FROM comments")
            jdbcTemplate.execute("DELETE FROM posts")
        }

        Given("Comment 를 저장하면") {
            val post = postRepository.save(Post.create(userId = 1L, title = "제목", content = "내용"))
            val comment = Comment.create(post = post, userId = 2L, content = "댓글")
            val saved = commentRepository.save(comment)

            When("findById 로 조회하면") {
                val found = commentRepository.findById(saved.id)
                Then("[R-01] Comment 가 반환되고 ZonedDateTime 이 UTC instant 기준으로 동일하게 복원된다") {
                    found.shouldNotBeNull()
                    found.post.id shouldBe post.id
                    found.userId shouldBe 2L
                    found.content shouldBe "댓글"
                    found.createdAt.toInstant() shouldBe saved.createdAt.toInstant()
                }
            }
        }

        Given("Comment 를 소프트 삭제하면") {
            val post = postRepository.save(Post.create(userId = 1L, title = "제목", content = "내용"))
            val comment = Comment.create(post = post, userId = 2L, content = "삭제될 댓글")
            val saved = commentRepository.save(comment)
            saved.softDelete(2L)
            commentRepository.save(saved)

            When("findById 로 조회하면") {
                val found = commentRepository.findById(saved.id)
                Then("[R-02] null 이 반환된다 (deletedAt IS NULL 필터)") {
                    found.shouldBeNull()
                }
            }
        }

        Given("Post 에 댓글이 여러 개 있고 일부가 삭제된 상태에서") {
            val post = postRepository.save(Post.create(userId = 1L, title = "제목", content = "내용"))
            commentRepository.save(Comment.create(post = post, userId = 2L, content = "댓글 1"))
            commentRepository.save(Comment.create(post = post, userId = 2L, content = "댓글 2"))
            val deleted = commentRepository.save(Comment.create(post = post, userId = 2L, content = "삭제됨"))
            deleted.softDelete(2L)
            commentRepository.save(deleted)

            When("findByPostId 로 조회하면") {
                val found = commentRepository.findByPostId(post.id)
                Then("[R-03] 삭제되지 않은 댓글만 반환된다") {
                    found.size shouldBe 2
                }
            }
        }
    }
}
