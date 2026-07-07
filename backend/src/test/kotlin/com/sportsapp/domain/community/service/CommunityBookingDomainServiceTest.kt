package com.sportsapp.domain.community.service

import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.entity.CommunityBooking
import com.sportsapp.domain.community.exception.NotCommunityHostException
import com.sportsapp.domain.community.exception.NotCommunityMemberException
import com.sportsapp.domain.community.gateway.SlotInfo
import com.sportsapp.domain.community.gateway.SlotInfoGateway
import com.sportsapp.domain.community.repository.CommunityBookingRepository
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.common.vo.SportCategory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class CommunityBookingDomainServiceTest : BehaviorSpec({

    val communityBookingRepository = mockk<CommunityBookingRepository>()
    val communityDomainService = mockk<CommunityDomainService>()
    val slotInfoGateway = mockk<SlotInfoGateway>()
    val communityBookingDomainService = CommunityBookingDomainService(
        communityBookingRepository = communityBookingRepository,
        communityDomainService = communityDomainService,
        slotInfoGateway = slotInfoGateway,
    )

    fun publicCommunity(hostUserId: Long = 100L) = Community.create(
        name = "주말 축구 모임",
        description = null,
        visibility = CommunityVisibility.PUBLIC,
        sportCategory = SportCategory.SOCCER,
        hostUserId = hostUserId,
    ).also { it.pullDomainEvents() }

    Given("방장이 예약 가능 slotId 를 연결하는 요청") {
        val community = publicCommunity(hostUserId = 100L)
        every { communityDomainService.getCommunity(1L, 100L) } returns community
        every { communityBookingRepository.findBy(1L, 10L) } returns null
        every { communityBookingRepository.save(any()) } answers { firstArg() }

        When("link 를 호출하면") {
            val booking = communityBookingDomainService.link(communityId = 1L, hostUserId = 100L, slotId = 10L)

            Then("CommunityBooking 이 생성된다") {
                booking.communityId shouldBe 1L
                booking.slotId shouldBe 10L
                booking.linkedByUserId shouldBe 100L
                verify { communityBookingRepository.save(any()) }
            }
        }
    }

    Given("방장이 아닌 사용자의 연결 시도") {
        val community = publicCommunity(hostUserId = 100L)
        every { communityDomainService.getCommunity(2L, 999L) } returns community

        When("link 를 호출하면") {
            Then("NotCommunityHostException 이 발생한다") {
                shouldThrow<NotCommunityHostException> {
                    communityBookingDomainService.link(communityId = 2L, hostUserId = 999L, slotId = 10L)
                }
            }
        }
    }

    Given("동일 slotId 가 이미 연결된 커뮤니티") {
        val community = publicCommunity(hostUserId = 100L)
        val existing = CommunityBooking.create(communityId = 3L, slotId = 30L, linkedByUserId = 100L)
        every { communityDomainService.getCommunity(3L, 100L) } returns community
        every { communityBookingRepository.findBy(3L, 30L) } returns existing

        When("동일 slotId 로 link 를 다시 호출하면") {
            val booking = communityBookingDomainService.link(communityId = 3L, hostUserId = 100L, slotId = 30L)

            Then("멱등하게 기존 링크를 반환하고 신규 저장하지 않는다") {
                booking shouldBe existing
                verify(exactly = 0) { communityBookingRepository.save(match { it.communityId == 3L && it.slotId == 30L }) }
            }
        }
    }

    Given("멤버가 연결 목록을 조회하는 요청") {
        val booking = CommunityBooking.create(communityId = 4L, slotId = 40L, linkedByUserId = 100L)
        val slotInfo = SlotInfo(
            facilityId = "facility-1",
            date = ZonedDateTime.now(),
            timeRange = "10:00-11:00",
            capacity = 8,
        )
        every { communityDomainService.requireActiveMember(4L, 200L) } returns Unit
        every { communityBookingRepository.findAllBy(4L) } returns listOf(booking)
        every { slotInfoGateway.findBy(40L) } returns slotInfo

        When("findLinked 를 호출하면") {
            val results = communityBookingDomainService.findLinked(communityId = 4L, requesterId = 200L)

            Then("SlotInfo(시설·일시·정원)가 함께 반환된다") {
                results shouldHaveSize 1
                results.first().facilityId shouldBe "facility-1"
                results.first().timeRange shouldBe "10:00-11:00"
                results.first().capacity shouldBe 8
            }

            Then("정원은 Slot 의 capacity 를 그대로 노출한다") {
                results.first().capacity shouldBe slotInfo.capacity
            }
        }
    }

    Given("PRIVATE 모임 비승인자의 연결 예약 조회") {
        every {
            communityDomainService.requireActiveMember(5L, 999L)
        } throws NotCommunityMemberException(5L, 999L)

        When("findLinked 를 호출하면") {
            Then("NotCommunityMemberException 이 발생한다") {
                shouldThrow<NotCommunityMemberException> {
                    communityBookingDomainService.findLinked(communityId = 5L, requesterId = 999L)
                }
            }
        }
    }

    Given("연결 예약이 없는 모임 조회") {
        every { communityDomainService.requireActiveMember(6L, 200L) } returns Unit
        every { communityBookingRepository.findAllBy(6L) } returns emptyList()

        When("findLinked 를 호출하면") {
            val results = communityBookingDomainService.findLinked(communityId = 6L, requesterId = 200L)

            Then("빈 목록을 정상 반환한다") {
                results.shouldBeEmpty()
            }
        }
    }
})
