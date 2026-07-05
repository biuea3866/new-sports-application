package com.sportsapp.domain.community.entity

import com.sportsapp.domain.community.event.CommunityCreatedEvent
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.community.vo.SportCategory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CommunityTest : BehaviorSpec({

    Given("공개 커뮤니티 개설 요청") {
        When("Community.create 를 호출하면") {
            val community = Community.create(
                name = "주말 축구 모임",
                description = "매주 토요일 축구",
                visibility = CommunityVisibility.PUBLIC,
                sportCategory = SportCategory.SOCCER,
                hostUserId = 1L,
            )

            Then("필드가 채워지고 isPublic() 이 true 다") {
                community.name shouldBe "주말 축구 모임"
                community.description shouldBe "매주 토요일 축구"
                community.visibility shouldBe CommunityVisibility.PUBLIC
                community.sportCategory shouldBe SportCategory.SOCCER
                community.currentHostUserId shouldBe 1L
                community.isPublic() shouldBe true
            }

            Then("CommunityCreatedEvent(hostUserId) 가 적재된다") {
                val events = community.pullDomainEvents()
                events shouldHaveSize 1
                val event = events.first()
                event.shouldBeInstanceOf<CommunityCreatedEvent>()
                (event as CommunityCreatedEvent).hostUserId shouldBe 1L
            }

            Then("pullDomainEvents 이후 다시 호출하면 빈 리스트다") {
                community.pullDomainEvents() shouldHaveSize 0
            }
        }
    }

    Given("비공개 커뮤니티") {
        val community = Community.create(
            name = "사내 배드민턴",
            description = null,
            visibility = CommunityVisibility.PRIVATE,
            sportCategory = SportCategory.BADMINTON,
            hostUserId = 2L,
        )

        When("isPublic 을 호출하면") {
            Then("false 를 반환한다") {
                community.isPublic() shouldBe false
            }
        }
    }

    Given("빈 이름으로 개설을 시도하면") {
        When("Community.create 를 호출하면") {
            Then("IllegalArgumentException 이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Community.create(
                        name = "   ",
                        description = null,
                        visibility = CommunityVisibility.PUBLIC,
                        sportCategory = SportCategory.ETC,
                        hostUserId = 1L,
                    )
                }
            }
        }
    }

    Given("방장 권한 위임") {
        val community = Community.create(
            name = "러닝크루",
            description = null,
            visibility = CommunityVisibility.PUBLIC,
            sportCategory = SportCategory.RUNNING,
            hostUserId = 1L,
        )
        community.pullDomainEvents()

        When("transferHostTo(newHostUserId) 를 호출하면") {
            community.transferHostTo(2L)

            Then("hostUserId 가 신규 방장으로 갱신된다") {
                community.currentHostUserId shouldBe 2L
            }
        }
    }

    Given("방장 요청자 검증") {
        val community = Community.create(
            name = "골프 모임",
            description = null,
            visibility = CommunityVisibility.PUBLIC,
            sportCategory = SportCategory.GOLF,
            hostUserId = 1L,
        )

        When("방장 본인이 requireHost 를 호출하면") {
            Then("예외 없이 통과한다") {
                community.requireHost(1L)
            }
        }

        When("방장이 아닌 사용자가 requireHost 를 호출하면") {
            Then("NotCommunityHostException 이 발생한다") {
                shouldThrow<com.sportsapp.domain.community.exception.NotCommunityHostException> {
                    community.requireHost(99L)
                }
            }
        }
    }
})
