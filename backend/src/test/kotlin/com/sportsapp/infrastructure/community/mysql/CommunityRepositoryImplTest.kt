package com.sportsapp.infrastructure.community.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.entity.CommunityMember
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.community.vo.SportCategory
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class CommunityRepositoryImplTest(
    @Autowired private val communityJpaRepository: CommunityJpaRepository,
    @Autowired private val communityCustomRepository: CommunityCustomRepositoryImpl,
    @Autowired private val communityMemberJpaRepository: CommunityMemberJpaRepository,
) : BaseIntegrationTest() {

    init {
        Given("공개 커뮤니티 저장") {
            val community = Community.create(
                name = "인프라 테스트 축구",
                description = "설명",
                visibility = CommunityVisibility.PUBLIC,
                sportCategory = SportCategory.SOCCER,
                hostUserId = 1L,
            )
            val saved = communityJpaRepository.save(community)

            When("findByIdAndDeletedAtIsNull 로 조회하면") {
                val found = communityJpaRepository.findByIdAndDeletedAtIsNull(saved.id)

                Then("저장된 필드가 그대로 복원된다") {
                    found.shouldNotBeNull()
                    found.name shouldBe "인프라 테스트 축구"
                    found.visibility shouldBe CommunityVisibility.PUBLIC
                    found.createdAt.shouldNotBeNull()
                }
            }
        }

        Given("소프트 삭제된 커뮤니티") {
            val community = Community.create(
                name = "삭제된 커뮤니티",
                description = null,
                visibility = CommunityVisibility.PUBLIC,
                sportCategory = SportCategory.ETC,
                hostUserId = 2L,
            )
            val saved = communityJpaRepository.save(community)
            saved.softDelete(null)
            communityJpaRepository.save(saved)

            When("findByIdAndDeletedAtIsNull 로 조회하면") {
                val found = communityJpaRepository.findByIdAndDeletedAtIsNull(saved.id)

                Then("deleted_at 필터가 적용되어 null 을 반환한다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("공개·비공개 커뮤니티가 섞여 있는 상태에서 키워드 검색") {
            val keyword = "인프라키워드${System.nanoTime()}"
            val publicCommunity = communityJpaRepository.save(
                Community.create(
                    name = "$keyword 공개",
                    description = null,
                    visibility = CommunityVisibility.PUBLIC,
                    sportCategory = SportCategory.RUNNING,
                    hostUserId = 3L,
                ),
            )
            communityJpaRepository.save(
                Community.create(
                    name = "$keyword 비공개",
                    description = null,
                    visibility = CommunityVisibility.PRIVATE,
                    sportCategory = SportCategory.RUNNING,
                    hostUserId = 4L,
                ),
            )

            When("findPublicByKeyword 를 호출하면") {
                val result = communityCustomRepository.findPublicByKeyword(keyword)

                Then("공개 커뮤니티만 반환된다") {
                    result shouldHaveSize 1
                    result.first().id shouldBe publicCommunity.id
                }
            }
        }

        Given("사용자가 ACTIVE 멤버로 가입된 커뮤니티") {
            val community = communityJpaRepository.save(
                Community.create(
                    name = "내 커뮤니티 조회용",
                    description = null,
                    visibility = CommunityVisibility.PUBLIC,
                    sportCategory = SportCategory.GOLF,
                    hostUserId = 5L,
                ),
            )
            val memberUserId = 12345L + System.nanoTime() % 100000
            communityMemberJpaRepository.save(CommunityMember.join(community.id, memberUserId, isPublic = true))

            When("findByMemberUserId 를 호출하면") {
                val result = communityCustomRepository.findByMemberUserId(memberUserId)

                Then("가입된 커뮤니티가 반환된다") {
                    result shouldHaveSize 1
                    result.first().id shouldBe community.id
                }
            }
        }
    }
}
