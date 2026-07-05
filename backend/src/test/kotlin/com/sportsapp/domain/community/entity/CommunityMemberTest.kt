package com.sportsapp.domain.community.entity

import com.sportsapp.domain.community.event.CommunityMemberJoinedEvent
import com.sportsapp.domain.community.event.CommunityMemberLeftEvent
import com.sportsapp.domain.community.exception.CannotKickHostException
import com.sportsapp.domain.community.exception.HostMustTransferBeforeLeaveException
import com.sportsapp.domain.community.exception.InvalidMembershipStateException
import com.sportsapp.domain.community.vo.CommunityRole
import com.sportsapp.domain.community.vo.MembershipStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CommunityMemberTest : BehaviorSpec({

    Given("공개 커뮤니티 가입") {
        When("CommunityMember.join(isPublic=true) 를 호출하면") {
            val member = CommunityMember.join(communityId = 10L, userId = 100L, isPublic = true)

            Then("즉시 ACTIVE 상태·MEMBER 역할이 되고 joinedAt 이 채워진다") {
                member.currentStatus shouldBe MembershipStatus.ACTIVE
                member.currentRole shouldBe CommunityRole.MEMBER
                member.currentJoinedAt.shouldNotBeNull()
            }

            Then("CommunityMemberJoinedEvent 가 적재된다") {
                val events = member.pullDomainEvents()
                events shouldHaveSize 1
                events.first().shouldBeInstanceOf<CommunityMemberJoinedEvent>()
            }
        }
    }

    Given("비공개 커뮤니티 가입") {
        When("CommunityMember.join(isPublic=false) 를 호출하면") {
            val member = CommunityMember.join(communityId = 10L, userId = 101L, isPublic = false)

            Then("PENDING_APPROVAL 상태가 되고 joinedAt 은 null 이다") {
                member.currentStatus shouldBe MembershipStatus.PENDING_APPROVAL
                member.currentJoinedAt.shouldBeNull()
            }

            Then("이벤트는 아직 적재되지 않는다") {
                member.pullDomainEvents() shouldHaveSize 0
            }
        }
    }

    Given("PENDING_APPROVAL 멤버") {
        val member = CommunityMember.join(communityId = 10L, userId = 102L, isPublic = false)
        member.pullDomainEvents()

        When("approve() 를 호출하면") {
            member.approve()

            Then("ACTIVE 로 전이되고 joinedAt 이 채워진다") {
                member.currentStatus shouldBe MembershipStatus.ACTIVE
                member.currentJoinedAt.shouldNotBeNull()
            }

            Then("CommunityMemberJoinedEvent 가 적재된다") {
                val events = member.pullDomainEvents()
                events shouldHaveSize 1
                events.first().shouldBeInstanceOf<CommunityMemberJoinedEvent>()
            }
        }
    }

    Given("이미 ACTIVE 인 멤버") {
        val member = CommunityMember.join(communityId = 10L, userId = 103L, isPublic = true)
        member.pullDomainEvents()

        When("approve() 를 다시 호출하면") {
            Then("InvalidMembershipStateException 이 발생한다") {
                shouldThrow<InvalidMembershipStateException> {
                    member.approve()
                }
            }
        }
    }

    Given("ACTIVE 인 일반 멤버(MEMBER)") {
        val member = CommunityMember.join(communityId = 10L, userId = 104L, isPublic = true)
        member.pullDomainEvents()

        When("kick() 을 호출하면") {
            member.kick()

            Then("KICKED 로 전이된다") {
                member.currentStatus shouldBe MembershipStatus.KICKED
            }

            Then("CommunityMemberLeftEvent 가 적재된다") {
                val events = member.pullDomainEvents()
                events shouldHaveSize 1
                events.first().shouldBeInstanceOf<CommunityMemberLeftEvent>()
            }
        }
    }

    Given("HOST 멤버") {
        val host = CommunityMember.createHost(communityId = 10L, userId = 1L)
        host.pullDomainEvents()

        When("kick() 을 호출하면") {
            Then("CannotKickHostException 이 발생한다") {
                shouldThrow<CannotKickHostException> {
                    host.kick()
                }
            }
        }

        When("위임 없이 leave() 를 호출하면") {
            Then("HostMustTransferBeforeLeaveException 이 발생한다") {
                shouldThrow<HostMustTransferBeforeLeaveException> {
                    host.leave()
                }
            }
        }

        When("demoteToMember() 이후 leave() 를 호출하면") {
            host.demoteToMember()
            host.leave()

            Then("LEFT 로 전이된다") {
                host.currentStatus shouldBe MembershipStatus.LEFT
            }
        }
    }

    Given("ACTIVE 인 일반 멤버") {
        val member = CommunityMember.join(communityId = 10L, userId = 105L, isPublic = true)
        member.pullDomainEvents()

        When("leave() 를 호출하면") {
            member.leave()

            Then("LEFT 로 전이되고 CommunityMemberLeftEvent 가 적재된다") {
                member.currentStatus shouldBe MembershipStatus.LEFT
                val events = member.pullDomainEvents()
                events shouldHaveSize 1
                events.first().shouldBeInstanceOf<CommunityMemberLeftEvent>()
            }
        }
    }

    Given("LEFT 로 종료된 멤버") {
        val member = CommunityMember.join(communityId = 10L, userId = 106L, isPublic = true)
        member.pullDomainEvents()
        member.leave()

        When("leave() 를 다시 호출하면") {
            Then("InvalidMembershipStateException 이 발생한다") {
                shouldThrow<InvalidMembershipStateException> {
                    member.leave()
                }
            }
        }
    }

    Given("promoteToHost/demoteToMember") {
        val member = CommunityMember.join(communityId = 10L, userId = 107L, isPublic = true)

        When("promoteToHost() 를 호출하면") {
            member.promoteToHost()
            Then("role 이 HOST 가 된다") {
                member.currentRole shouldBe CommunityRole.HOST
            }
        }

        When("다시 demoteToMember() 를 호출하면") {
            member.demoteToMember()
            Then("role 이 MEMBER 가 된다") {
                member.currentRole shouldBe CommunityRole.MEMBER
            }
        }
    }
})
