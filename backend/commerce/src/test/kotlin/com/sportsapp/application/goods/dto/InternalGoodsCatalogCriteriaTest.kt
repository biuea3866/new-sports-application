package com.sportsapp.application.goods.dto

import com.sportsapp.domain.goods.vo.SellerType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 공급자 페이징 규약을 잠근다 — 특히 **`size` 를 절삭하지 않는다**는 결정이다.
 *
 * 소비자(파사드)가 `min(size, 100)` 으로 자른 뒤 여러 도메인 병합을 위해
 * `PageRequest.of(0, (page + 1) * cappedSize)` 창을 요청하고 자기가 `drop/take` 한다. 공급자가
 * 여기서 다시 절삭하면 page ≥ 1 결과가 유실돼 섀도 응답 동일성(S2-06·S2-15)이 깨진다.
 * 형제 공급자 `InternalProgramCatalogCriteriaTest`(S2-04)·`InternalRecruitmentCatalogCriteriaTest`(S2-05)와
 * 같은 규약을 고정한다.
 */
class InternalGoodsCatalogCriteriaTest : BehaviorSpec({

    Given("파사드가 요청한 넓은 창(page=0, size=300)") {
        val criteria = InternalGoodsCatalogCriteria(keyword = null, sellerType = null, page = 0, size = 300)

        When("toPageable 로 변환하면") {
            val pageable = criteria.toPageable()

            Then("size 를 절삭하지 않고 그대로 위임한다 (100 으로 자르면 page>=1 결과가 유실된다)") {
                pageable.pageSize shouldBe 300
                pageable.pageNumber shouldBe 0
            }
        }
    }

    Given("판매자 유형 필터가 있는 요청(page=2, size=20, sellerType=B2B)") {
        val criteria = InternalGoodsCatalogCriteria(
            keyword = "저지",
            sellerType = SellerType.B2B,
            page = 2,
            size = 20,
        )

        When("toPageable 로 변환하면") {
            val pageable = criteria.toPageable()

            Then("page·size 를 그대로 전달한다 — 정렬은 리포지토리 구현체가 고정한다") {
                pageable.pageNumber shouldBe 2
                pageable.pageSize shouldBe 20
                pageable.sort.isSorted shouldBe false
            }

            Then("keyword·sellerType 은 도메인 검색 조건으로 그대로 보존된다") {
                criteria.keyword shouldBe "저지"
                criteria.sellerType shouldBe SellerType.B2B
            }
        }
    }
})
