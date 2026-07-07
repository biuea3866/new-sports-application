package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.LinkCommunityBookingCommand
import com.sportsapp.domain.community.entity.CommunityBooking
import com.sportsapp.domain.community.exception.NotCommunityHostException
import com.sportsapp.domain.community.service.CommunityBookingDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class LinkCommunityBookingUseCaseTest : BehaviorSpec({

    val communityBookingDomainService = mockk<CommunityBookingDomainService>()
    val useCase = LinkCommunityBookingUseCase(communityBookingDomainService)

    Given("방장의 슬롯 연결 요청") {
        // CommunityBooking.create()는 JPA 영속화 이전 상태라 createdAt(lateinit)이 미설정이다.
        // Response.of()가 createdAt을 읽으므로 relaxed mock으로 필요한 필드만 스텁한다.
        val booking = mockk<CommunityBooking>(relaxed = true) {
            every { id } returns 1L
            every { communityId } returns 1L
            every { slotId } returns 10L
            every { linkedByUserId } returns 100L
            every { createdAt } returns ZonedDateTime.now()
        }
        every { communityBookingDomainService.link(1L, 100L, 10L) } returns booking

        When("execute 를 호출하면") {
            val result = useCase.execute(
                LinkCommunityBookingCommand(communityId = 1L, hostUserId = 100L, slotId = 10L),
            )

            Then("CommunityBookingResponse 로 변환되어 반환된다") {
                result.communityId shouldBe 1L
                result.slotId shouldBe 10L
                result.linkedByUserId shouldBe 100L
            }
        }
    }

    Given("방장이 아닌 사용자의 연결 요청") {
        every {
            communityBookingDomainService.link(2L, 999L, 10L)
        } throws NotCommunityHostException(2L, 999L)

        When("execute 를 호출하면") {
            Then("NotCommunityHostException 이 전파된다") {
                shouldThrow<NotCommunityHostException> {
                    useCase.execute(LinkCommunityBookingCommand(communityId = 2L, hostUserId = 999L, slotId = 10L))
                }
            }
        }
    }
})
