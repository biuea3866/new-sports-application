package com.sportsapp.domain.community.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.entity.CommunityMember
import com.sportsapp.domain.community.exception.CannotKickHostException
import com.sportsapp.domain.community.exception.HostMustTransferBeforeLeaveException
import com.sportsapp.domain.community.exception.NotCommunityHostException
import com.sportsapp.domain.community.exception.NotCommunityMemberException
import com.sportsapp.domain.community.repository.CommunityMemberRepository
import com.sportsapp.domain.community.repository.CommunityRepository
import com.sportsapp.domain.community.vo.CommunityRole
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.community.vo.MembershipStatus
import com.sportsapp.domain.community.vo.SportCategory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class CommunityDomainServiceTest : BehaviorSpec({

    val communityRepository = mockk<CommunityRepository>()
    val communityMemberRepository = mockk<CommunityMemberRepository>()
    val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
    val communityDomainService = CommunityDomainService(
        communityRepository = communityRepository,
        communityMemberRepository = communityMemberRepository,
        domainEventPublisher = domainEventPublisher,
    )

    fun publicCommunity(hostUserId: Long = 100L) = Community.create(
        name = "주말 축구 모임",
        description = null,
        visibility = CommunityVisibility.PUBLIC,
        sportCategory = SportCategory.SOCCER,
        hostUserId = hostUserId,
    ).also { it.pullDomainEvents() }

    fun privateCommunity(hostUserId: Long = 100L) = Community.create(
        name = "사내 배드민턴",
        description = null,
        visibility = CommunityVisibility.PRIVATE,
        sportCategory = SportCategory.BADMINTON,
        hostUserId = hostUserId,
    ).also { it.pullDomainEvents() }

    Given("커뮤니티 개설 요청") {
        every { communityRepository.save(any()) } answers { firstArg() }
        every { communityMemberRepository.save(any()) } answers { firstArg() }

        When("create 를 호출하면") {
            val community = communityDomainService.create(
                name = "주말 축구 모임",
                description = "매주 토요일",
                visibility = CommunityVisibility.PUBLIC,
                sportCategory = SportCategory.SOCCER,
                hostUserId = 100L,
            )

            Then("커뮤니티와 방장 멤버십이 저장되고 CommunityCreatedEvent가 발행된다") {
                community.currentHostUserId shouldBe 100L
                verify { communityRepository.save(any()) }
                verify { communityMemberRepository.save(match { it.currentRole == CommunityRole.HOST }) }
                verify { domainEventPublisher.publishAll(any()) }
            }
        }
    }

    Given("공개 커뮤니티에 가입 요청") {
        val community = publicCommunity()
        every { communityRepository.findById(1L) } returns community
        every { communityMemberRepository.save(any()) } answers { firstArg() }

        When("join 을 호출하면") {
            val member = communityDomainService.join(communityId = 1L, userId = 200L)

            Then("즉시 ACTIVE 멤버가 되고 MemberJoined 이벤트가 발행된다") {
                member.currentStatus shouldBe MembershipStatus.ACTIVE
                verify { domainEventPublisher.publishAll(match { it.isNotEmpty() }) }
            }
        }
    }

    Given("비공개 커뮤니티에 가입 요청") {
        val community = privateCommunity()
        every { communityRepository.findById(2L) } returns community
        every { communityMemberRepository.save(any()) } answers { firstArg() }

        When("join 을 호출하면") {
            val member = communityDomainService.join(communityId = 2L, userId = 201L)

            Then("PENDING_APPROVAL 상태가 되고, 이후 approve 를 거쳐야 ACTIVE가 된다") {
                member.currentStatus shouldBe MembershipStatus.PENDING_APPROVAL
            }
        }
    }

    Given("존재하지 않는 커뮤니티에 가입 요청") {
        every { communityRepository.findById(999L) } returns null

        When("join 을 호출하면") {
            Then("ResourceNotFoundException 이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    communityDomainService.join(communityId = 999L, userId = 1L)
                }
            }
        }
    }

    Given("PENDING_APPROVAL 멤버가 있는 비공개 커뮤니티") {
        val community = privateCommunity(hostUserId = 100L)
        val pending = CommunityMember.join(communityId = 3L, userId = 201L, isPublic = false)
        pending.pullDomainEvents()
        every { communityRepository.findById(3L) } returns community
        every { communityMemberRepository.findBy(3L, 201L) } returns pending
        every { communityMemberRepository.save(any()) } answers { firstArg() }

        When("방장이 approve 를 호출하면") {
            val approved = communityDomainService.approve(communityId = 3L, requesterId = 100L, targetUserId = 201L)

            Then("ACTIVE 로 전이되고 이벤트가 발행된다") {
                approved.currentStatus shouldBe MembershipStatus.ACTIVE
                verify { domainEventPublisher.publishAll(match { it.isNotEmpty() }) }
            }
        }

        When("방장이 아닌 사용자가 approve 를 호출하면") {
            Then("NotCommunityHostException 이 발생한다") {
                shouldThrow<NotCommunityHostException> {
                    communityDomainService.approve(communityId = 3L, requesterId = 999L, targetUserId = 201L)
                }
            }
        }
    }

    Given("ACTIVE 인 일반 멤버가 있는 커뮤니티") {
        val community = publicCommunity(hostUserId = 100L)
        val activeMember = CommunityMember.join(communityId = 4L, userId = 202L, isPublic = true)
        activeMember.pullDomainEvents()
        every { communityRepository.findById(4L) } returns community
        every { communityMemberRepository.findBy(4L, 202L) } returns activeMember
        every { communityMemberRepository.save(any()) } answers { firstArg() }

        When("방장이 kick 을 호출하면") {
            communityDomainService.kick(communityId = 4L, requesterId = 100L, targetUserId = 202L)

            Then("KICKED 로 전이되고 MemberLeft 이벤트가 발행된다") {
                activeMember.currentStatus shouldBe MembershipStatus.KICKED
                verify { domainEventPublisher.publishAll(match { it.isNotEmpty() }) }
            }
        }

        When("방장이 아닌 사용자가 kick 을 호출하면") {
            Then("NotCommunityHostException 이 발생한다") {
                shouldThrow<NotCommunityHostException> {
                    communityDomainService.kick(communityId = 4L, requesterId = 999L, targetUserId = 202L)
                }
            }
        }
    }

    Given("HOST 본인을 강퇴 대상으로 지정한 요청") {
        val community = publicCommunity(hostUserId = 100L)
        val hostMember = CommunityMember.createHost(communityId = 5L, userId = 100L)
        hostMember.pullDomainEvents()
        every { communityRepository.findById(5L) } returns community
        every { communityMemberRepository.findBy(5L, 100L) } returns hostMember

        When("방장이 스스로를 kick 대상으로 호출하면") {
            Then("CannotKickHostException 이 발생한다") {
                shouldThrow<CannotKickHostException> {
                    communityDomainService.kick(communityId = 5L, requesterId = 100L, targetUserId = 100L)
                }
            }
        }
    }

    Given("존재하지 않는 대상을 강퇴하려는 요청") {
        val community = publicCommunity(hostUserId = 100L)
        every { communityRepository.findById(6L) } returns community
        every { communityMemberRepository.findBy(6L, 999L) } returns null

        When("kick 을 호출하면") {
            Then("ResourceNotFoundException 이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    communityDomainService.kick(communityId = 6L, requesterId = 100L, targetUserId = 999L)
                }
            }
        }
    }

    Given("HOST가 위임 없이 탈퇴를 시도하는 요청") {
        val community = publicCommunity(hostUserId = 100L)
        val hostMember = CommunityMember.createHost(communityId = 7L, userId = 100L)
        hostMember.pullDomainEvents()
        every { communityRepository.findById(7L) } returns community
        every { communityMemberRepository.findBy(7L, 100L) } returns hostMember

        When("leave 를 호출하면") {
            Then("HostMustTransferBeforeLeaveException 이 발생한다") {
                shouldThrow<HostMustTransferBeforeLeaveException> {
                    communityDomainService.leave(communityId = 7L, userId = 100L)
                }
            }
        }
    }

    Given("ACTIVE 인 일반 멤버의 탈퇴 요청") {
        val community = publicCommunity(hostUserId = 100L)
        val member = CommunityMember.join(communityId = 8L, userId = 203L, isPublic = true)
        member.pullDomainEvents()
        every { communityRepository.findById(8L) } returns community
        every { communityMemberRepository.findBy(8L, 203L) } returns member
        every { communityMemberRepository.save(any()) } answers { firstArg() }

        When("leave 를 호출하면") {
            communityDomainService.leave(communityId = 8L, userId = 203L)

            Then("LEFT 로 전이되고 이벤트가 발행된다") {
                member.currentStatus shouldBe MembershipStatus.LEFT
                verify { domainEventPublisher.publishAll(match { it.isNotEmpty() }) }
            }
        }
    }

    Given("방장 권한 위임 요청") {
        val community = publicCommunity(hostUserId = 100L)
        val oldHost = CommunityMember.createHost(communityId = 9L, userId = 100L)
        oldHost.pullDomainEvents()
        val newHostMember = CommunityMember.join(communityId = 9L, userId = 204L, isPublic = true)
        newHostMember.pullDomainEvents()
        every { communityRepository.findById(9L) } returns community
        every { communityRepository.save(any()) } answers { firstArg() }
        every { communityMemberRepository.findBy(9L, 100L) } returns oldHost
        every { communityMemberRepository.findBy(9L, 204L) } returns newHostMember
        every { communityMemberRepository.save(any()) } answers { firstArg() }

        When("현재 방장이 transfer 를 호출하면") {
            communityDomainService.transfer(communityId = 9L, requesterId = 100L, newHostUserId = 204L)

            Then("기존 방장은 MEMBER, 신규 사용자는 HOST가 된다") {
                oldHost.currentRole shouldBe CommunityRole.MEMBER
                newHostMember.currentRole shouldBe CommunityRole.HOST
                community.currentHostUserId shouldBe 204L
            }
        }
    }

    Given("공개 커뮤니티 상세 조회") {
        val community = publicCommunity(hostUserId = 100L)
        every { communityRepository.findById(10L) } returns community

        When("비멤버가 getCommunity 를 호출하면") {
            val found = communityDomainService.getCommunity(communityId = 10L, requesterId = 999L)

            Then("멤버십 검증 없이 조회된다") {
                found.id shouldBe community.id
            }
        }
    }

    Given("비공개 커뮤니티 상세 조회") {
        val community = privateCommunity(hostUserId = 100L)
        val activeMember = CommunityMember.join(communityId = 11L, userId = 205L, isPublic = true)
        activeMember.pullDomainEvents()
        every { communityRepository.findById(11L) } returns community
        every { communityMemberRepository.findActiveBy(11L, 205L) } returns activeMember
        every { communityMemberRepository.findActiveBy(11L, 999L) } returns null

        When("ACTIVE 멤버가 getCommunity 를 호출하면") {
            val found = communityDomainService.getCommunity(communityId = 11L, requesterId = 205L)

            Then("정상 조회된다") {
                found.id shouldBe community.id
            }
        }

        When("비멤버가 getCommunity 를 호출하면") {
            Then("NotCommunityMemberException 이 발생한다") {
                shouldThrow<NotCommunityMemberException> {
                    communityDomainService.getCommunity(communityId = 11L, requesterId = 999L)
                }
            }
        }
    }

    Given("공개 커뮤니티 키워드 목록 조회") {
        val community = publicCommunity()
        every { communityRepository.findPublicByKeyword("축구") } returns listOf(community)

        When("findPublicCommunities 를 호출하면") {
            val result = communityDomainService.findPublicCommunities(keyword = "축구")

            Then("공개 커뮤니티 목록이 반환된다") {
                result shouldHaveSize 1
            }
        }
    }

    Given("커뮤니티 ACTIVE 멤버 목록 조회 — FR-13 ②") {
        val activeMember = CommunityMember.join(communityId = 13L, userId = 206L, isPublic = true)
        activeMember.pullDomainEvents()
        every { communityMemberRepository.findActiveBy(13L, 206L) } returns activeMember
        every { communityMemberRepository.findActiveBy(13L, 999L) } returns null
        every { communityMemberRepository.findActiveByCommunityId(13L) } returns listOf(activeMember)

        When("ACTIVE 멤버가 findMembers 를 호출하면") {
            val members = communityDomainService.findMembers(communityId = 13L, requesterId = 206L)

            Then("멤버 목록이 반환된다") {
                members shouldHaveSize 1
            }
        }

        When("커뮤니티 멤버가 아닌 사용자가 findMembers 를 호출하면") {
            Then("NotCommunityMemberException 이 발생한다") {
                shouldThrow<NotCommunityMemberException> {
                    communityDomainService.findMembers(communityId = 13L, requesterId = 999L)
                }
            }
        }

        When("컨텍스트 방(contextType=COMMUNITY) 게스트가 contextId=communityId 로 findMembers 를 호출하면") {
            Then("community_members ACTIVE 레코드가 없어 동일하게 거부된다") {
                shouldThrow<NotCommunityMemberException> {
                    communityDomainService.findMembers(communityId = 13L, requesterId = 999L)
                }
            }
        }
    }

    Given("탈퇴·강퇴된 사용자의 멤버십 범위 조회") {
        val leftMember = CommunityMember.join(communityId = 14L, userId = 207L, isPublic = true)
        leftMember.pullDomainEvents()
        leftMember.leave()
        every { communityMemberRepository.findActiveBy(14L, 207L) } returns null

        When("findMembers 를 호출하면") {
            Then("ACTIVE 가 아니므로 NotCommunityMemberException 이 발생한다") {
                shouldThrow<NotCommunityMemberException> {
                    communityDomainService.findMembers(communityId = 14L, requesterId = 207L)
                }
            }
        }
    }

    Given("커뮤니티 활성 멤버 수 집계") {
        val activeMember = CommunityMember.join(communityId = 16L, userId = 208L, isPublic = true)
        activeMember.pullDomainEvents()
        every { communityMemberRepository.findActiveByCommunityId(16L) } returns listOf(activeMember)

        When("countActiveMembers 를 호출하면") {
            val count = communityDomainService.countActiveMembers(communityId = 16L)

            Then("ACTIVE 멤버 수가 반환된다") {
                count shouldBe 1
            }
        }
    }

    Given("내가 가입한 커뮤니티 목록 조회") {
        val community = publicCommunity()
        every { communityRepository.findByMemberUserId(300L) } returns listOf(community)

        When("findMyCommunities 를 호출하면") {
            val result = communityDomainService.findMyCommunities(userId = 300L)

            Then("내가 ACTIVE 멤버인 커뮤니티 목록이 반환된다") {
                result shouldHaveSize 1
            }
        }
    }
})
