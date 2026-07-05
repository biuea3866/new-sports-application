package com.sportsapp.infrastructure.community.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.community.entity.CommunityMember
import com.sportsapp.domain.community.vo.MembershipStatus
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class CommunityMemberRepositoryImplTest(
    @Autowired private val communityMemberJpaRepository: CommunityMemberJpaRepository,
    @Autowired private val communityMemberRepositoryImpl: CommunityMemberRepositoryImpl,
) : BaseIntegrationTest() {

    init {
        Given("PENDING_APPROVAL 멤버가 저장된 상태") {
            val communityId = 900L + System.nanoTime() % 100000
            val userId = 1000L + System.nanoTime() % 100000
            val member = communityMemberJpaRepository.save(
                CommunityMember.join(communityId, userId, isPublic = false),
            )

            When("findBy 로 조회하면") {
                val found = communityMemberRepositoryImpl.findBy(communityId, userId)

                Then("상태와 무관하게 레코드가 조회된다") {
                    found.shouldNotBeNull()
                    found.currentStatus shouldBe MembershipStatus.PENDING_APPROVAL
                }
            }

            When("findActiveBy 로 조회하면") {
                val found = communityMemberRepositoryImpl.findActiveBy(communityId, userId)

                Then("ACTIVE 가 아니므로 null 이 반환된다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("ACTIVE 멤버가 저장된 상태") {
            val communityId = 901L + System.nanoTime() % 100000
            val userId = 2000L + System.nanoTime() % 100000
            communityMemberJpaRepository.save(CommunityMember.join(communityId, userId, isPublic = true))

            When("findActiveBy 로 조회하면") {
                val found = communityMemberRepositoryImpl.findActiveBy(communityId, userId)

                Then("ACTIVE 멤버가 반환된다") {
                    found.shouldNotBeNull()
                    found.currentStatus shouldBe MembershipStatus.ACTIVE
                }
            }

            When("findActiveByCommunityId 로 조회하면") {
                val members = communityMemberRepositoryImpl.findActiveByCommunityId(communityId)

                Then("ACTIVE 멤버 목록에 포함된다") {
                    members shouldHaveSize 1
                }
            }
        }

        Given("KICKED 로 전이된 멤버") {
            val communityId = 902L + System.nanoTime() % 100000
            val userId = 3000L + System.nanoTime() % 100000
            val member = communityMemberJpaRepository.save(CommunityMember.join(communityId, userId, isPublic = true))
            member.kick()
            communityMemberJpaRepository.save(member)

            When("findActiveByCommunityId 로 조회하면") {
                val members = communityMemberRepositoryImpl.findActiveByCommunityId(communityId)

                Then("KICKED 멤버는 제외된다") {
                    members shouldHaveSize 0
                }
            }
        }
    }
}
