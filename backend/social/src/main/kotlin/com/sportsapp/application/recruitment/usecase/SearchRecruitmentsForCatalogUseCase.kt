package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.dto.InternalRecruitmentCatalogItemResponse
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * catalog 통합검색(BE-07)이 fan-out 하는 recruitment 원격 공급 UseCase (S2-05).
 *
 * `page`·`size`를 그대로 받아 도메인 페이징만 수행한다 — 여러 도메인 병합·정렬은 edge 파사드의
 * 책임이라 여기서는 수행하지 않는다 (S2-03 규약 원문과 동일).
 */
@Service
class SearchRecruitmentsForCatalogUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(keyword: String?, page: Int, size: Int): List<InternalRecruitmentCatalogItemResponse> {
        val pageable = PageRequest.of(page, size)
        return recruitmentDomainService.searchOpenRecruitments(keyword, pageable).content
            .map(InternalRecruitmentCatalogItemResponse::of)
    }
}
