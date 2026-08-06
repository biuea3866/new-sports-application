package com.sportsapp.application.goods.dto

import com.sportsapp.domain.goods.vo.SellerType
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

/**
 * edge catalog 통합검색(BE-07)의 `CatalogSearchGateway.searchGoods` 원격 구현(2단계)이 호출하는
 * 공급자 엔드포인트(`GET /internal/catalog/goods`) 조회 조건 (S2-03).
 *
 * `page`·`size` 를 개별 `Int` 인자로 넘기면 호출부에서 위치가 뒤바뀌어도 컴파일이 통과해 조용히
 * 오동작한다 — 인접한 동일 타입 인자를 값 객체로 묶어 그 경로를 없앤다. 이름은 레포 관례를 따른다
 * (`FacilityCriteria`·`CatalogSearchCriteria`·`PostCriteria`). 형제 공급자
 * `InternalProgramCatalogCriteria`(S2-04)·`InternalRecruitmentCatalogCriteria`(S2-05)와 대칭이다.
 *
 * ## `size` 상한을 **걸지 않는다** (의도된 결정)
 *
 * 상한은 소비자(파사드) 책임이고 이미 적용된 뒤에 온다 — `CatalogSearchCriteria` 가
 * `cappedSize = min(size, 100)` 로 자른 다음, 여러 도메인을 병합·정렬해 in-memory 페이징하려고
 * **`PageRequest.of(0, (page + 1) * cappedSize)`** 창을 요청하고 `drop(page * cappedSize).take(cappedSize)`
 * 로 자기가 잘라낸다. 따라서 공급자가 다시 100 으로 절삭하면 **page ≥ 1 결과가 유실된다** —
 * 사용자 page=2 는 창 300 을 요구하는데 100 만 받아 파사드가 200 을 drop 하면 빈 목록이 된다.
 * 1단계 로컬 어댑터도 절삭하지 않아, 절삭을 넣는 순간 섀도 응답 동일성(S2-06·S2-15)이 깨진다.
 * 이 결정은 [InternalGoodsCatalogCriteriaTest] 가 잠근다. 창이 page 에 선형 증가하는 것은
 * 소비자 쪽 부채다 (후속 리스크 등록부 R-28).
 *
 * 카테고리·가격 조건은 파사드가 쓰지 않아 노출하지 않는다 — `GoodsDomainService.search` 에 null 로
 * 전달한다(1단계 로컬 어댑터와 동일).
 */
data class InternalGoodsCatalogCriteria(
    val keyword: String?,
    val sellerType: SellerType?,
    val page: Int,
    val size: Int,
) {
    fun toPageable(): Pageable = PageRequest.of(page, size)

    companion object {
        /** `@RequestParam(defaultValue = ..)` 는 컴파일 상수만 받으므로 문자열로 둔다. */
        const val DEFAULT_PAGE_PARAM = "0"
        const val DEFAULT_SIZE_PARAM = "20"
    }
}
