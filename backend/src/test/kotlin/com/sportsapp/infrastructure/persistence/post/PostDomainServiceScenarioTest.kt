package com.sportsapp.infrastructure.persistence.post

import com.sportsapp.BaseMongoIntegrationTest
import com.sportsapp.domain.post.PostDomainService
import com.sportsapp.domain.post.PostType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired

/**
 * [S-01] PostDomainService.createPost → findById 전체 흐름이 정상 동작한다
 */
class PostDomainServiceScenarioTest(
    @Autowired private val postDomainService: PostDomainService,
) : BaseMongoIntegrationTest() {

    init {
        Given("PostDomainService로 게시글 생성 시") {
            val post = postDomainService.createPost(
                type = PostType.FREE,
                title = "시나리오 게시글",
                content = "시나리오 테스트 내용",
                userId = 10L,
                writer = "scenarioUser",
            )

            Then("[S-01] 저장된 게시글의 id가 null이 아니다") {
                post.id shouldNotBe null
            }

            Then("[S-01] title이 정확히 저장된다") {
                post.title shouldBe "시나리오 게시글"
            }

            Then("[S-01] userId가 정확히 저장된다") {
                post.userId shouldBe 10L
            }

            When("저장된 id로 getPost를 호출하면") {
                val found = postDomainService.getPost(requireNotNull(post.id))
                Then("[S-01] 동일한 게시글이 반환된다") {
                    found.title shouldBe "시나리오 게시글"
                    found.userId shouldBe 10L
                    found.type shouldBe PostType.FREE
                }
            }
        }
    }
}
