package com.sportsapp.application.facility.dto

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

/**
 * edge catalog 통합검색(BE-07)의 `CatalogSearchGateway.searchPrograms` 원격 구현(2단계)이 호출하는
 * 공급자 엔드포인트(`GET /internal/catalog/programs`) 조회 조건 (S2-04).
 *
 * ## `size` 상한을 **걸지 않는다** (의도된 결정)
 *
 * 상한은 소비자(파사드)의 책임이고 이미 적용된 뒤에 온다 — `CatalogSearchCriteria` 가
 * `cappedSize = min(size, 100)` 로 자른 다음, 여러 도메인을 병합·정렬해 in-memory 페이징하기 위해
 * **`PageRequest.of(0, (page + 1) * cappedSize)`** 라는 넉넉한 창을 각 도메인에 요청하고
 * `drop(page * cappedSize).take(cappedSize)` 로 자기가 잘라낸다.
 *
 * 따라서 공급자가 여기서 다시 100 으로 절삭하면 **page ≥ 1 결과가 유실된다** — 사용자 page=2 는
 * 창 300 을 요구하는데 100 만 받아 파사드가 200 을 drop 하면 빈 목록이 된다. 1단계 로컬 어댑터도
 * 절삭하지 않으므로, 절삭을 넣는 순간 섀도 응답 동일성(S2-06·S2-15)이 깨진다.
 *
 * 이 결정은 [InternalProgramCatalogCriteriaTest] 가 "받은 size 를 그대로 위임한다"로 잠근다.
 * 형제 공급자(S2-03 commerce·S2-05 social)도 같은 규약을 따라야 세 공급자의 페이징이 대칭이 된다.
 *
 * 창 크기가 page 에 선형 증가하는 것(deep page 폭증)은 **소비자 쪽 부채**다 — 파사드가 page 상한을
 * 갖지 않는 데서 오고, 공급자가 절삭으로 방어할 문제가 아니다 (후속 리스크 등록부 R-28).
 *
 * 정렬은 [com.sportsapp.domain.facility.repository.ProgramCustomRepository.searchForCatalog] 구현체가
 * createdAt desc 로 고정하므로 여기서는 offset·limit 만 구성한다.
 */
data class InternalProgramCatalogCriteria(
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
