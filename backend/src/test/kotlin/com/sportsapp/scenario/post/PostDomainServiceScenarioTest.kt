package com.sportsapp.scenario.post

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.post.PostDomainService
import com.sportsapp.domain.post.PostRepository
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class PostDomainServiceScenarioTest(
    @Autowired private val postDomainService: PostDomainService,
    @Autowired private val postRepository: PostRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        beforeEach {
            jdbcTemplate.execute("DELETE FROM posts")
        }

        Given("[S-01] createPost 를 호출하면 Post 가 DB 에 저장되고 findById 로 조회된다") {
            Then("Post 가 저장되고 findById 로 조회된다") {
                val post = postDomainService.createPost(userId = 1L, title = "테스트 제목", content = "테스트 내용")
                val found = postRepository.findById(post.id)
                found.shouldNotBeNull()
                found.title shouldBe "테스트 제목"
                found.content shouldBe "테스트 내용"
            }
        }

        Given("[S-02] deletePost 를 호출한 후 findById 로 조회하면 null 이 반환된다") {
            Then("null 이 반환된다") {
                val post = postDomainService.createPost(userId = 1L, title = "삭제될 Post", content = "내용")
                postDomainService.deletePost(postId = post.id, userId = 1L)
                val found = postRepository.findById(post.id)
                found.shouldBeNull()
            }
        }

        Given("createPost 에 빈 제목을 전달하면") {
            Then("[S-03] IllegalArgumentException 이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    postDomainService.createPost(userId = 1L, title = "", content = "내용")
                }
            }
        }

        Given("createPost 에 201자 제목을 전달하면") {
            val longTitle = "a".repeat(201)
            Then("[S-03] IllegalArgumentException 이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    postDomainService.createPost(userId = 1L, title = longTitle, content = "내용")
                }
            }
        }

        Given("존재하지 않는 postId 로 deletePost 를 호출하면") {
            Then("[S-03] ResourceNotFoundException 이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    postDomainService.deletePost(postId = 99999L, userId = 1L)
                }
            }
        }
    }
}
