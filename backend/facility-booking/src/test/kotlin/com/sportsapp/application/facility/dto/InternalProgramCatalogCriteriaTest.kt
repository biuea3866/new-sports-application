package com.sportsapp.application.facility.dto

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 공급자 페이징 규약을 잠근다 — 특히 **`size` 를 절삭하지 않는다**는 결정이다.
 *
 * 소비자(파사드)가 `min(size, 100)` 으로 자른 뒤 여러 도메인 병합을 위해
 * `PageRequest.of(0, (page + 1) * cappedSize)` 창을 요청하고 자기가 `drop/take` 한다. 공급자가
 * 여기서 다시 절삭하면 page ≥ 1 결과가 유실돼 섀도 응답 동일성(S2-06·S2-15)이 깨진다.
 * 이 테스트가 없으면 다음 사람이 "상한이 없다"를 누락으로 보고 절삭을 넣는다.
 */
class InternalProgramCatalogCriteriaTest : BehaviorSpec({

    Given("파사드가 요청한 넓은 창(page=0, size=300)") {
        val criteria = InternalProgramCatalogCriteria(keyword = null, page = 0, size = 300)

        When("toPageable 로 변환하면") {
            val pageable = criteria.toPageable()

            Then("size 를 절삭하지 않고 그대로 위임한다 (100 으로 자르면 page>=1 결과가 유실된다)") {
                pageable.pageSize shouldBe 300
                pageable.pageNumber shouldBe 0
            }
        }
    }

    Given("일반적인 단일 페이지 요청(page=2, size=20)") {
        val criteria = InternalProgramCatalogCriteria(keyword = "PT", page = 2, size = 20)

        When("toPageable 로 변환하면") {
            val pageable = criteria.toPageable()

            Then("page·size 를 그대로 전달한다 — 정렬은 리포지토리 구현체가 createdAt desc 로 고정한다") {
                pageable.pageNumber shouldBe 2
                pageable.pageSize shouldBe 20
                pageable.sort.isSorted shouldBe false
            }
        }
    }

    Given("컨트롤러 기본값 상수") {
        Then("@RequestParam 이 쓰는 문자열 형태로 한 곳에만 정의된다 (매직값 이원화 방지)") {
            InternalProgramCatalogCriteria.DEFAULT_PAGE_PARAM shouldBe "0"
            InternalProgramCatalogCriteria.DEFAULT_SIZE_PARAM shouldBe "20"
        }
    }
})
