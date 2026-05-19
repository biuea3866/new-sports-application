package com.sportsapp.infrastructure.persistence.post

import com.sportsapp.BaseMongoIntegrationTest
import com.sportsapp.domain.post.Post
import com.sportsapp.domain.post.PostType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * [R-01] PostMongoRepository save → findById 라운드트립으로 모든 필드(ZonedDateTime zone 포함)가 보존된다
 */
class PostRepositoryRoundtripTest(
    @Autowired private val postMongoRepository: PostMongoRepository,
) : BaseMongoIntegrationTest() {

    init {
        Given("Post 도큐먼트를 MongoDB에 저장했을 때") {
            val createdAt = ZonedDateTime.of(2026, 5, 20, 12, 0, 0, 0, ZoneOffset.UTC)
            val post = Post.create(
                type = PostType.FREE,
                title = "테스트 게시글",
                content = "테스트 내용입니다",
                userId = 42L,
                writer = "tester",
                createdAt = createdAt,
            )
            val document = PostDocument.fromDomain(post)
            val saved = postMongoRepository.save(document)

            When("findById로 조회하면") {
                val found = postMongoRepository.findById(requireNotNull(saved.id)).orElse(null)

                Then("[R-01] 저장된 id가 null이 아니다") {
                    saved.id shouldNotBe null
                }

                Then("[R-01] title 필드가 동일하다") {
                    found?.title shouldBe "테스트 게시글"
                }

                Then("[R-01] content 필드가 동일하다") {
                    found?.content shouldBe "테스트 내용입니다"
                }

                Then("[R-01] userId 필드가 동일하다") {
                    found?.userId shouldBe 42L
                }

                Then("[R-01] type 필드가 동일하다") {
                    found?.type shouldBe PostType.FREE.name
                }

                Then("[R-01] writer 필드가 동일하다") {
                    found?.writer shouldBe "tester"
                }

                Then("[R-01] createdAt을 도메인으로 변환 시 동일한 instant를 가진다") {
                    val domain = found?.toDomain()
                    domain?.createdAt?.toInstant() shouldBe createdAt.toInstant()
                }
            }
        }
    }
}
