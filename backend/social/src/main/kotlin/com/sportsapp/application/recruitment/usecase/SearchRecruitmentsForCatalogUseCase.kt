package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.dto.InternalRecruitmentCatalogCriteria
import com.sportsapp.application.recruitment.dto.InternalRecruitmentCatalogItemResponse
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * catalog 통합검색(BE-07)이 fan-out 하는 recruitment 원격 공급 UseCase (S2-05).
 *
 * [InternalRecruitmentCatalogCriteria]가 담은 `page`·`size`를 **절삭 없이** 도메인 페이징에 위임한다 —
 * 상한은 소비자(파사드)가 창을 계산하기 전에 이미 적용했고, 여기서 다시 자르면 page >= 1 결과가
 * 유실된다(근거·잠금 테스트는 그 값 객체 KDoc 참고). 여러 도메인 병합·정렬도 edge 파사드의 책임이라
 * 여기서 수행하지 않는다 (S2-03 규약 원문과 동일).
 */
@Service
class SearchRecruitmentsForCatalogUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(criteria: InternalRecruitmentCatalogCriteria): List<InternalRecruitmentCatalogItemResponse> =
        recruitmentDomainService.searchOpenRecruitments(criteria.keyword, criteria.toPageable()).content
            .map(InternalRecruitmentCatalogItemResponse::of)
}
