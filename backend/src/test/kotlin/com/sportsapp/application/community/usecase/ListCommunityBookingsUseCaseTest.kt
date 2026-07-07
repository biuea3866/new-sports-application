package com.sportsapp.application.community.usecase

import com.sportsapp.domain.community.dto.CommunityBookingResult
import com.sportsapp.domain.community.exception.NotCommunityMemberException
import com.sportsapp.domain.community.service.CommunityBookingDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class ListCommunityBookingsUseCaseTest : BehaviorSpec({

    val communityBookingDomainService = mockk<CommunityBookingDomainService>()
    val useCase = ListCommunityBookingsUseCase(communityBookingDomainService)

    Given("멤버의 연결 목록 조회 요청") {
        val result = CommunityBookingResult(
            id = 1L,
            communityId = 1L,
            slotId = 10L,
            linkedByUserId = 100L,
            facilityId = "facility-1",
            date = ZonedDateTime.now(),
            timeRange = "10:00-11:00",
            capacity = 8,
        )
        every { communityBookingDomainService.findLinked(1L, 200L) } returns listOf(result)

        When("execute 를 호출하면") {
            val responses = useCase.execute(communityId = 1L, requesterId = 200L)

            Then("SlotInfo 가 포함된 목록으로 변환된다") {
                responses shouldHaveSize 1
                responses.first().facilityId shouldBe "facility-1"
                responses.first().capacity shouldBe 8
            }
        }
    }

    Given("연결 예약이 없는 모임 조회 요청") {
        every { communityBookingDomainService.findLinked(2L, 200L) } returns emptyList()

        When("execute 를 호출하면") {
            val responses = useCase.execute(communityId = 2L, requesterId = 200L)

            Then("빈 목록을 정상 반환한다") {
                responses.shouldBeEmpty()
            }
        }
    }

    Given("PRIVATE 모임 비승인자의 조회 요청") {
        every {
            communityBookingDomainService.findLinked(3L, 999L)
        } throws NotCommunityMemberException(3L, 999L)

        When("execute 를 호출하면") {
            Then("NotCommunityMemberException 이 전파된다") {
                shouldThrow<NotCommunityMemberException> {
                    useCase.execute(communityId = 3L, requesterId = 999L)
                }
            }
        }
    }
})
