package com.sportsapp.domain.community.entity

import com.sportsapp.domain.community.event.CommunityMemberJoinedEvent
import com.sportsapp.domain.community.event.CommunityMemberLeftEvent
import com.sportsapp.domain.community.exception.AlreadyCommunityMemberException
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

/**
 * `CommunityMember.id`도 Community와 동일한 이유(`@GeneratedValue(IDENTITY)` + Kotlin `val`)로
 * 순수 도메인 테스트에서 리플렉션 없이는 값을 바꿀 수 없다 — 리뷰 p2-② 검증용.
 */
private fun forceId(member: CommunityMember, value: Long) {
    val field = CommunityMember::class.java.getDeclaredField("id")
    field.isAccessible = true
    field.setLong(member, value)
}

class CommunityMemberTest : BehaviorSpec({

    Given("공개 커뮤니티 가입") {
        When("CommunityMember.join(isPublic=true) 를 호출하면") {
            val member = CommunityMember.join(communityId = 10L, userId = 100L, isPublic = true)

            Then("즉시 ACTIVE 상태·MEMBER 역할이 되고 joinedAt 이 채워진다") {
                member.currentStatus shouldBe MembershipStatus.ACTIVE
                member.currentRole shouldBe CommunityRole.MEMBER
                member.currentJoinedAt.shouldNotBeNull()
            }

            Then("join() 시점에는 이벤트가 적재되지 않는다 (id 미확정, 리뷰 p2-②)") {
                member.pullDomainEvents() shouldHaveSize 0
            }
        }

        When("save 후 registerJoinedEvent() 를 호출하면") {
            val member = CommunityMember.join(communityId = 10L, userId = 108L, isPublic = true)
            forceId(member, 77L)
            member.registerJoinedEvent()

            Then("CommunityMemberJoinedEvent.aggregateId 가 저장된 id(77)와 일치한다 (리뷰 p2-②)") {
                val events = member.pullDomainEvents()
                events shouldHaveSize 1
                val event = events.first()
                event.shouldBeInstanceOf<CommunityMemberJoinedEvent>()
                event.aggregateId shouldBe 77L
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

    Given("LEFT(탈퇴)했던 멤버 — 리뷰 p2-①") {
        val member = CommunityMember.join(communityId = 10L, userId = 109L, isPublic = true)
        member.leave()
        member.pullDomainEvents() // 탈퇴 시점의 CommunityMemberLeftEvent를 비워 rejoin 이벤트만 검증한다

        When("공개 커뮤니티에 rejoin(isPublic=true) 하면") {
            member.rejoin(isPublic = true)

            Then("새 row INSERT 없이 ACTIVE 로 재활성화되고 joinedAt 이 갱신된다") {
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

    Given("KICKED(강퇴)됐던 멤버가 비공개 커뮤니티에 재가입하는 경우") {
        val member = CommunityMember.join(communityId = 10L, userId = 110L, isPublic = true)
        member.kick()
        member.pullDomainEvents() // 강퇴 시점의 CommunityMemberLeftEvent를 비워 rejoin 이벤트만 검증한다

        When("rejoin(isPublic=false) 하면") {
            member.rejoin(isPublic = false)

            Then("PENDING_APPROVAL 로 재진입하고 joinedAt 은 null 이다") {
                member.currentStatus shouldBe MembershipStatus.PENDING_APPROVAL
                member.currentJoinedAt.shouldBeNull()
            }

            Then("승인 전이라 이벤트는 적재되지 않는다") {
                member.pullDomainEvents() shouldHaveSize 0
            }
        }
    }

    Given("이미 ACTIVE 인 멤버 — 리뷰 p2-①") {
        val member = CommunityMember.join(communityId = 10L, userId = 111L, isPublic = true)

        When("rejoin() 을 호출하면") {
            Then("AlreadyCommunityMemberException 이 발생한다 (UNIQUE 제약 500 대신 명시적 409)") {
                shouldThrow<AlreadyCommunityMemberException> {
                    member.rejoin(isPublic = true)
                }
            }
        }
    }

    Given("이미 PENDING_APPROVAL 인 멤버") {
        val member = CommunityMember.join(communityId = 10L, userId = 112L, isPublic = false)

        When("rejoin() 을 호출하면") {
            Then("AlreadyCommunityMemberException 이 발생한다") {
                shouldThrow<AlreadyCommunityMemberException> {
                    member.rejoin(isPublic = false)
                }
            }
        }
    }

    Given("HOST 역할의 멤버") {
        val host = CommunityMember.createHost(communityId = 10L, userId = 113L)

        When("isHost 를 조회하면") {
            Then("true 를 반환한다") {
                host.isHost shouldBe true
            }
        }
    }

    Given("MEMBER 역할의 멤버") {
        val member = CommunityMember.join(communityId = 10L, userId = 114L, isPublic = true)

        When("isHost 를 조회하면") {
            Then("false 를 반환한다") {
                member.isHost shouldBe false
            }
        }

        When("promoteToHost() 이후 isHost 를 조회하면") {
            member.promoteToHost()

            Then("true 를 반환한다") {
                member.isHost shouldBe true
            }
        }
    }
})
