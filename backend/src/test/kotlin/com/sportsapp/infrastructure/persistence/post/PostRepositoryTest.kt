package com.sportsapp.infrastructure.persistence.post

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.post.Post
import com.sportsapp.domain.post.PostRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class PostRepositoryTest(
    @Autowired private val postRepository: PostRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        beforeEach {
            jdbcTemplate.execute("DELETE FROM posts")
        }

        Given("Post 를 저장하면") {
            val post = Post.create(userId = 1L, title = "제목", content = "내용")
            val saved = postRepository.save(post)

            When("findById 로 조회하면") {
                val found = postRepository.findById(saved.id)
                Then("[R-01] Post 가 반환되고 ZonedDateTime 이 UTC instant 기준으로 동일하게 복원된다") {
                    found.shouldNotBeNull()
                    found.title shouldBe "제목"
                    found.content shouldBe "내용"
                    found.userId shouldBe 1L
                    found.createdAt.toInstant() shouldBe saved.createdAt.toInstant()
                    found.updatedAt.toInstant() shouldBe saved.updatedAt.toInstant()
                }
            }
        }

        Given("Post 를 소프트 삭제하면") {
            val post = Post.create(userId = 1L, title = "삭제될 Post", content = "내용")
            val saved = postRepository.save(post)
            saved.softDelete(1L)
            postRepository.save(saved)

            When("findById 로 조회하면") {
                val found = postRepository.findById(saved.id)
                Then("[R-02] null 이 반환된다 (deletedAt IS NULL 필터)") {
                    found.shouldBeNull()
                }
            }
        }

        Given("여러 Post 가 저장되어 있고 일부가 소프트 삭제된 상태에서") {
            val post1 = postRepository.save(Post.create(userId = 1L, title = "활성 Post 1", content = "내용"))
            val post2 = postRepository.save(Post.create(userId = 1L, title = "활성 Post 2", content = "내용"))
            val post3 = postRepository.save(Post.create(userId = 1L, title = "삭제된 Post", content = "내용"))
            post3.softDelete(1L)
            postRepository.save(post3)

            When("findByUserId 로 조회하면") {
                val found = postRepository.findByUserId(1L)
                Then("[R-03] 삭제되지 않은 Post 만 반환된다") {
                    found.size shouldBe 2
                    found.map { it.id }.containsAll(listOf(post1.id, post2.id)) shouldBe true
                }
            }
        }
    }
}
