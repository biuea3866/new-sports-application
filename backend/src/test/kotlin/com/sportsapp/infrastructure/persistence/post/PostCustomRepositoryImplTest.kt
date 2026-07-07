package com.sportsapp.infrastructure.persistence.post

import com.sportsapp.infrastructure.post.mysql.PostJpaRepository
import com.sportsapp.infrastructure.post.mysql.PostCustomRepositoryImpl

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.dto.PostSearchCriteria
import com.sportsapp.domain.post.vo.PostType
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate

class PostCustomRepositoryImplTest(
    @Autowired private val postJpaRepository: PostJpaRepository,
    @Autowired private val postCustomRepositoryImpl: PostCustomRepositoryImpl,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        beforeEach {
            jdbcTemplate.execute("DELETE FROM posts")
        }

        Given("[R-01] FREE 타입 Post 25건, NOTICE 5건이 저장된 상태에서") {
            repeat(25) { i ->
                postJpaRepository.save(Post.create(userId = 1L, title = "자유글 $i", content = "내용", type = PostType.FREE))
            }
            repeat(5) { i ->
                postJpaRepository.save(Post.create(userId = 2L, title = "공지 $i", content = "내용", type = PostType.NOTICE))
            }

            When("[R-01] type=FREE, size=20으로 조회하면") {
                val criteria = PostSearchCriteria(type = PostType.FREE, userId = null, keyword = null)
                val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
                val result = postCustomRepositoryImpl.findByCriteria(criteria, pageable)

                Then("정확히 20건이 반환되고 totalElements=25이다") {
                    result.content.size shouldBe 20
                    result.totalElements shouldBe 25L
                    result.content.all { it.type == PostType.FREE } shouldBe true
                }
            }
        }

        Given("[R-02] keyword='풋살'이 포함된 Post와 없는 Post가 혼재할 때") {
            postJpaRepository.save(Post.create(userId = 1L, title = "풋살 모집", content = "같이 해요", type = PostType.FREE))
            postJpaRepository.save(Post.create(userId = 1L, title = "농구 모집", content = "풋살도 환영", type = PostType.FREE))
            postJpaRepository.save(Post.create(userId = 1L, title = "야구 모집", content = "야구합시다", type = PostType.FREE))

            When("[R-02] keyword='풋살'로 조회하면") {
                val criteria = PostSearchCriteria(type = null, userId = null, keyword = "풋살")
                val pageable = PageRequest.of(0, 10)
                val result = postCustomRepositoryImpl.findByCriteria(criteria, pageable)

                Then("title 또는 content에 풋살이 포함된 2건이 반환된다") {
                    result.totalElements shouldBe 2L
                }
            }
        }

        Given("[R-03] 여러 사용자가 Post를 올린 상태에서") {
            postJpaRepository.save(Post.create(userId = 10L, title = "글 A", content = "내용", type = PostType.FREE))
            postJpaRepository.save(Post.create(userId = 10L, title = "글 B", content = "내용", type = PostType.NOTICE))
            postJpaRepository.save(Post.create(userId = 20L, title = "글 C", content = "내용", type = PostType.FREE))

            When("[R-03] userId=10 필터를 적용하면") {
                val criteria = PostSearchCriteria(type = null, userId = 10L, keyword = null)
                val pageable = PageRequest.of(0, 10)
                val result = postCustomRepositoryImpl.findByCriteria(criteria, pageable)

                Then("userId=10의 Post 2건만 반환된다") {
                    result.totalElements shouldBe 2L
                    result.content.all { it.userId == 10L } shouldBe true
                }
            }
        }

        Given("soft-delete된 Post가 포함된 상태에서") {
            val active = postJpaRepository.save(Post.create(userId = 1L, title = "활성", content = "내용", type = PostType.FREE))
            val deleted = postJpaRepository.save(Post.create(userId = 1L, title = "삭제됨", content = "내용", type = PostType.FREE))
            deleted.softDelete(1L)
            postJpaRepository.save(deleted)

            When("필터 없이 조회하면") {
                val criteria = PostSearchCriteria(type = null, userId = null, keyword = null)
                val pageable = PageRequest.of(0, 10)
                val result = postCustomRepositoryImpl.findByCriteria(criteria, pageable)

                Then("활성 Post 1건만 반환된다") {
                    result.totalElements shouldBe 1L
                    result.content.first().id shouldBe active.id
                }
            }
        }

        Given("createdAt 정렬 검증을 위한 Post 3건") {
            val post1 = postJpaRepository.save(Post.create(userId = 1L, title = "첫 번째", content = "내용", type = PostType.FREE))
            postJpaRepository.save(Post.create(userId = 1L, title = "두 번째", content = "내용", type = PostType.FREE))
            val post3 = postJpaRepository.save(Post.create(userId = 1L, title = "세 번째", content = "내용", type = PostType.FREE))

            When("기본 정렬(createdAt desc)로 조회하면") {
                val criteria = PostSearchCriteria(type = null, userId = null, keyword = null)
                val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
                val result = postCustomRepositoryImpl.findByCriteria(criteria, pageable)

                Then("최신 Post가 먼저 반환된다") {
                    result.content.first().id shouldBe post3.id
                    result.content.last().id shouldBe post1.id
                }
            }
        }

        Given("[R-04] 전역 게시글과 서로 다른 모임 소속 게시글이 혼재할 때") {
            postJpaRepository.save(Post.create(userId = 1L, title = "전역글", content = "내용", type = PostType.FREE))
            postJpaRepository.save(
                Post.createInCommunity(
                    userId = 1L,
                    title = "모임10 글",
                    content = "내용",
                    type = PostType.FREE,
                    communityId = 10L,
                    sportCategory = SportCategory.SOCCER,
                    authorIsHost = true,
                    communityIsPublic = true,
                ),
            )
            postJpaRepository.save(
                Post.createInCommunity(
                    userId = 1L,
                    title = "모임20 글",
                    content = "내용",
                    type = PostType.FREE,
                    communityId = 20L,
                    sportCategory = SportCategory.TENNIS,
                    authorIsHost = true,
                    communityIsPublic = true,
                ),
            )

            When("[R-04] communityId=10 으로 조회하면") {
                val criteria = PostSearchCriteria(type = null, userId = null, keyword = null, communityId = 10L)
                val pageable = PageRequest.of(0, 10)
                val result = postCustomRepositoryImpl.findByCriteria(criteria, pageable)

                Then("모임10 소속 게시글 1건만 반환된다") {
                    result.totalElements shouldBe 1L
                    result.content.first().currentCommunityId shouldBe 10L
                }
            }
        }

        Given("[R-05] 종목이 서로 다른 게시글이 혼재할 때") {
            postJpaRepository.save(
                Post.create(userId = 1L, title = "축구글", content = "내용", type = PostType.FREE, sportCategory = SportCategory.SOCCER),
            )
            postJpaRepository.save(
                Post.create(userId = 1L, title = "테니스글", content = "내용", type = PostType.FREE, sportCategory = SportCategory.TENNIS),
            )
            postJpaRepository.save(Post.create(userId = 1L, title = "종목없음", content = "내용", type = PostType.FREE))

            When("[R-05] sportCategory=SOCCER 로 조회하면") {
                val criteria = PostSearchCriteria(type = null, userId = null, keyword = null, sportCategory = SportCategory.SOCCER)
                val pageable = PageRequest.of(0, 10)
                val result = postCustomRepositoryImpl.findByCriteria(criteria, pageable)

                Then("SOCCER 종목 게시글 1건만 반환된다") {
                    result.totalElements shouldBe 1L
                    result.content.first().currentSportCategory shouldBe SportCategory.SOCCER
                }
            }
        }

        Given("[R-06] 전역 노출 게시글과 비공개 모임 게시글이 혼재할 때") {
            postJpaRepository.save(Post.create(userId = 1L, title = "전역글", content = "내용", type = PostType.FREE))
            postJpaRepository.save(
                Post.createInCommunity(
                    userId = 1L,
                    title = "공개모임 글",
                    content = "내용",
                    type = PostType.FREE,
                    communityId = 30L,
                    sportCategory = SportCategory.RUNNING,
                    authorIsHost = true,
                    communityIsPublic = true,
                ),
            )
            postJpaRepository.save(
                Post.createInCommunity(
                    userId = 1L,
                    title = "비공개모임 글",
                    content = "내용",
                    type = PostType.FREE,
                    communityId = 40L,
                    sportCategory = SportCategory.RUNNING,
                    authorIsHost = true,
                    communityIsPublic = false,
                ),
            )

            When("[R-06] globalFeedOnly=true 로 조회하면") {
                val criteria = PostSearchCriteria(type = null, userId = null, keyword = null, globalFeedOnly = true)
                val pageable = PageRequest.of(0, 10)
                val result = postCustomRepositoryImpl.findByCriteria(criteria, pageable)

                Then("전역 노출(globalListed=true) 게시글 2건만 반환되고 비공개 모임 글은 제외된다") {
                    result.totalElements shouldBe 2L
                    result.content.all { it.isGlobalListed } shouldBe true
                    result.content.none { it.currentCommunityId == 40L } shouldBe true
                }
            }
        }
    }
}
