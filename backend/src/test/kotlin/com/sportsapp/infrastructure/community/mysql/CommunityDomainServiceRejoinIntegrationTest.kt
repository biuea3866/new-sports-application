package com.sportsapp.infrastructure.community.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.community.exception.AlreadyCommunityMemberException
import com.sportsapp.domain.community.exception.NotCommunityMemberException
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.community.vo.MembershipStatus
import com.sportsapp.domain.community.vo.SportCategory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

/**
 * 리뷰 p2-①·p2-②·p3 재검증 — 실제 MySQL(Testcontainers) 위에서 [CommunityDomainService]를
 * 실행해 다음을 증명한다.
 * - p2-① 재가입/중복가입이 `community_members` UNIQUE(community_id, user_id, deleted_at)
 *   제약 위반(500) 없이 처리된다.
 * - p2-② `create()`가 반환한 커뮤니티의 id가 실제로 확정(0 초과)된다 — 이벤트 aggregateId
 *   정합성은 [com.sportsapp.domain.community.entity.CommunityTest]에서 리플렉션으로 직접 검증.
 * - p3 방장 위임은 ACTIVE 멤버에게만 가능하다.
 */
class CommunityDomainServiceRejoinIntegrationTest(
    @Autowired private val communityDomainService: CommunityDomainService,
) : BaseIntegrationTest() {

    init {
        Given("커뮤니티 개설") {
            When("create 를 호출하면") {
                val community = communityDomainService.create(
                    name = "실 DB id 확정 검증용",
                    description = null,
                    visibility = CommunityVisibility.PUBLIC,
                    sportCategory = SportCategory.SOCCER,
                    hostUserId = 900001L,
                )

                Then("id 가 실제로 확정된다 (0 초과, 리뷰 p2-②)") {
                    community.id shouldBeGreaterThan 0L
                }
            }
        }

        Given("이미 ACTIVE 인 멤버가 재가입을 시도하는 경우 — 리뷰 p2-①") {
            val community = communityDomainService.create(
                name = "중복가입 방지 실DB 테스트",
                description = null,
                visibility = CommunityVisibility.PUBLIC,
                sportCategory = SportCategory.SOCCER,
                hostUserId = 900002L,
            )
            val userId = 900003L
            communityDomainService.join(community.id, userId)

            When("join 을 다시 호출하면") {
                Then("UNIQUE 제약 500 대신 AlreadyCommunityMemberException(409) 이 발생한다") {
                    shouldThrow<AlreadyCommunityMemberException> {
                        communityDomainService.join(community.id, userId)
                    }
                }
            }
        }

        Given("탈퇴(LEFT)한 사용자가 같은 공개 커뮤니티에 재가입하는 경우 — 리뷰 p2-①") {
            val community = communityDomainService.create(
                name = "탈퇴 후 재가입 실DB 테스트",
                description = null,
                visibility = CommunityVisibility.PUBLIC,
                sportCategory = SportCategory.SOCCER,
                hostUserId = 900004L,
            )
            val userId = 900005L
            communityDomainService.join(community.id, userId)
            communityDomainService.leave(community.id, userId)

            When("join 을 다시 호출하면") {
                val rejoined = communityDomainService.join(community.id, userId)

                Then("UNIQUE 제약 위반 없이 새 row INSERT 대신 기존 row가 ACTIVE 로 재활성화된다") {
                    rejoined.currentStatus shouldBe MembershipStatus.ACTIVE
                }
            }
        }

        Given("강퇴(KICKED)된 사용자가 비공개 커뮤니티에 재가입하는 경우 — 리뷰 p2-①") {
            val community = communityDomainService.create(
                name = "강퇴 후 재가입 실DB 테스트",
                description = null,
                visibility = CommunityVisibility.PRIVATE,
                sportCategory = SportCategory.BADMINTON,
                hostUserId = 900006L,
            )
            val userId = 900007L
            communityDomainService.join(community.id, userId)
            communityDomainService.approve(community.id, 900006L, userId)
            communityDomainService.kick(community.id, 900006L, userId)

            When("join 을 다시 호출하면") {
                val rejoined = communityDomainService.join(community.id, userId)

                Then("비공개 커뮤니티라 PENDING_APPROVAL 로 재진입한다") {
                    rejoined.currentStatus shouldBe MembershipStatus.PENDING_APPROVAL
                }
            }
        }

        Given("방장이 ACTIVE 가 아닌(PENDING_APPROVAL) 사용자에게 권한 위임을 시도하는 경우 — 리뷰 p3") {
            val community = communityDomainService.create(
                name = "위임 가드 실DB 테스트",
                description = null,
                visibility = CommunityVisibility.PRIVATE,
                sportCategory = SportCategory.GOLF,
                hostUserId = 900008L,
            )
            val pendingUserId = 900009L
            communityDomainService.join(community.id, pendingUserId)

            When("transfer 를 호출하면") {
                Then("NotCommunityMemberException 이 발생해 PENDING_APPROVAL 에게 방장 권한이 넘어가지 않는다") {
                    shouldThrow<NotCommunityMemberException> {
                        communityDomainService.transfer(community.id, 900008L, pendingUserId)
                    }
                }
            }
        }
    }
}
