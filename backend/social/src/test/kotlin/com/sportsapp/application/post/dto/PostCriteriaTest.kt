package com.sportsapp.application.post.dto

import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.post.vo.PostType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PostCriteriaTest : BehaviorSpec({

    Given("communityId·sportCategory·globalFeedOnly가 지정된 Criteria가 주어졌을 때") {
        val criteria = PostCriteria(
            type = PostType.FREE,
            userId = null,
            keyword = null,
            communityId = 5L,
            sportCategory = SportCategory.SOCCER,
            globalFeedOnly = false,
            page = 0,
            size = 10,
        )

        When("toSearchCriteria를 호출하면") {
            val searchCriteria = criteria.toSearchCriteria()

            Then("communityId·sportCategory·globalFeedOnly가 그대로 매핑된다") {
                searchCriteria.communityId shouldBe 5L
                searchCriteria.sportCategory shouldBe SportCategory.SOCCER
                searchCriteria.globalFeedOnly shouldBe false
            }
        }
    }

    Given("공백 keyword가 주어졌을 때") {
        val criteria = PostCriteria(
            type = null,
            userId = null,
            keyword = "   ",
            communityId = null,
            sportCategory = null,
            globalFeedOnly = false,
            page = 0,
            size = 10,
        )

        When("toSearchCriteria를 호출하면") {
            val searchCriteria = criteria.toSearchCriteria()

            Then("keyword가 null로 정규화된다") {
                searchCriteria.keyword shouldBe null
            }
        }
    }

    Given("size가 MAX_PAGE_SIZE를 초과했을 때") {
        val criteria = PostCriteria(
            type = null,
            userId = null,
            keyword = null,
            communityId = null,
            sportCategory = null,
            globalFeedOnly = false,
            page = 0,
            size = 200,
        )

        When("toPageable을 호출하면") {
            val pageable = criteria.toPageable()

            Then("size가 100으로 cap된다") {
                pageable.pageSize shouldBe PostCriteria.MAX_PAGE_SIZE
            }
        }
    }

    Given("communityId·sportCategory가 지정되지 않았을 때") {
        val criteria = PostCriteria(
            type = null,
            userId = null,
            keyword = null,
            communityId = null,
            sportCategory = null,
            globalFeedOnly = true,
            page = 0,
            size = 10,
        )

        When("toSearchCriteria를 호출하면") {
            val searchCriteria = criteria.toSearchCriteria()

            Then("communityId·sportCategory는 null로 필터가 적용되지 않고 globalFeedOnly만 전달된다") {
                searchCriteria.communityId shouldBe null
                searchCriteria.sportCategory shouldBe null
                searchCriteria.globalFeedOnly shouldBe true
            }
        }
    }
})
