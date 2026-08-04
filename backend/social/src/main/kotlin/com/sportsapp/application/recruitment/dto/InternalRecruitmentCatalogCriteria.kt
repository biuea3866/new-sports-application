package com.sportsapp.application.recruitment.dto

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

/**
 * catalog 통합검색(BE-07)의 `CatalogSearchGateway.searchRecruitments` 원격 구현(2단계)이 호출하는
 * 공급자 엔드포인트(`GET /internal/catalog/recruitments`) 조회 조건 (S2-05).
 *
 * `page`·`size` 를 개별 `Int` 인자로 넘기면 호출부에서 위치가 뒤바뀌어도 컴파일이 통과해 조용히
 * 오동작한다 — 인접한 동일 타입 인자를 값 객체로 묶어 그 경로를 없앤다. 이름은 레포 관례를 따른다
 * (`FacilityCriteria`·`CatalogSearchCriteria`·`PostCriteria`). 형제 공급자
 * `InternalProgramCatalogCriteria`(S2-04)와 같은 형태로 두어 두 엔드포인트가 대칭이 되게 한다.
 *
 * ## `size` 상한을 **걸지 않는다** (의도된 결정)
 *
 * 상한은 소비자(파사드) 책임이고 이미 적용된 뒤에 온다 — `CatalogSearchCriteria` 가
 * `cappedSize = min(size, 100)` 로 자른 다음 병합·정렬을 위해
 * `PageRequest.of(0, (page + 1) * cappedSize)` 창을 요청하고 `drop/take` 로 자기가 잘라낸다.
 * 공급자가 다시 절삭하면 **page ≥ 1 결과가 유실**되고 섀도 응답 동일성(S2-06·S2-15)이 깨진다.
 * 1단계 로컬 어댑터도 절삭하지 않는다. 창이 page 에 선형 증가하는 것은 소비자 쪽 부채다
 * (후속 리스크 등록부 R-28).
 *
 * 정렬은 [com.sportsapp.domain.recruitment.repository.RecruitmentCustomRepository] 구현체가
 * createdAt desc 로 고정하므로 여기서는 offset·limit 만 구성한다.
 */
data class InternalRecruitmentCatalogCriteria(
    val keyword: String?,
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
