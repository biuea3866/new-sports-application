package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.CreateCommunityCommand
import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.common.vo.SportCategory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class CreateCommunityUseCaseTest : BehaviorSpec({

    val communityDomainService = mockk<CommunityDomainService>()
    val useCase = CreateCommunityUseCase(communityDomainService)

    Given("커뮤니티 개설 커맨드") {
        // 실제 저장을 거치지 않은 mock — JpaAuditingBase.createdAt(lateinit)은 실 persist 시점에만
        // auditing listener 가 채우므로, 단위 테스트에서는 booking 도메인 관례(GetBookingUseCaseTest)와
        // 동일하게 relaxed mock 으로 필요한 필드만 스텁한다.
        val community = mockk<Community>(relaxed = true) {
            every { id } returns 1L
            every { name } returns "주말 축구 모임"
            every { description } returns "매주 토요일"
            every { visibility } returns CommunityVisibility.PUBLIC
            every { sportCategory } returns SportCategory.SOCCER
            every { currentHostUserId } returns 1L
            every { createdAt } returns ZonedDateTime.now()
        }
        every {
            communityDomainService.create(
                name = "주말 축구 모임",
                description = "매주 토요일",
                visibility = CommunityVisibility.PUBLIC,
                sportCategory = SportCategory.SOCCER,
                hostUserId = 1L,
            )
        } returns community

        When("execute 를 호출하면") {
            val command = CreateCommunityCommand(
                name = "주말 축구 모임",
                description = "매주 토요일",
                visibility = CommunityVisibility.PUBLIC,
                sportCategory = SportCategory.SOCCER,
                hostUserId = 1L,
            )
            val result = useCase.execute(command)

            Then("memberCount=1, roomId=null 인 CommunityResponse 가 반환된다") {
                result.name shouldBe "주말 축구 모임"
                result.memberCount shouldBe 1
                result.roomId shouldBe null
                verify { communityDomainService.create(any(), any(), any(), any(), any()) }
            }
        }
    }
})
