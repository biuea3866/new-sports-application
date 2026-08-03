package com.sportsapp.application.recruitment

import com.sportsapp.application.recruitment.usecase.SearchRecruitmentsForCatalogUseCase
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.entity.RecruitmentStatus
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

/**
 * edge catalog 통합검색(BE-07)이 fan-out 하는 recruitment 원격 공급 UseCase (S2-05).
 */
class SearchRecruitmentsForCatalogUseCaseTest : BehaviorSpec({

    val createdAt = ZonedDateTime.of(2026, 6, 2, 9, 0, 0, 0, ZoneOffset.UTC)

    fun mockRecruitment(
        id: Long,
        title: String,
        feeAmount: BigDecimal = BigDecimal("10000"),
        status: RecruitmentStatus = RecruitmentStatus.OPEN,
    ): Recruitment {
        // Recruitment.createdAt은 JPA @CreatedDate(lateinit) — 영속화 전 접근 시 예외가 나므로
        // ListMyApplicationsUseCaseTest와 동일하게 relaxed mockk로 스텁한다.
        val recruitment = mockk<Recruitment>(relaxed = true)
        every { recruitment.id } returns id
        every { recruitment.title } returns title
        every { recruitment.feeAmount } returns feeAmount
        every { recruitment.status } returns status
        every { recruitment.createdAt } returns createdAt
        return recruitment
    }

    Given("keyword로 오픈 모집을 검색할 때") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val useCase = SearchRecruitmentsForCatalogUseCase(recruitmentDomainService)
        val pageable = PageRequest.of(0, 20)
        val recruitment = mockRecruitment(id = 1L, title = "주말 축구 모임")
        val page: Page<Recruitment> = PageImpl(listOf(recruitment), pageable, 1L)
        every { recruitmentDomainService.searchOpenRecruitments("축구", pageable) } returns page

        When("execute(keyword=\"축구\", page=0, size=20)를 호출하면") {
            val result = useCase.execute(keyword = "축구", page = 0, size = 20)

            Then("계약 필드만 담은 응답을 반환한다") {
                result.size shouldBe 1
                result[0].sourceId shouldBe 1L
                result[0].title shouldBe "주말 축구 모임"
                result[0].price shouldBe BigDecimal("10000")
                result[0].status shouldBe RecruitmentStatus.OPEN
                result[0].createdAt shouldBe createdAt
            }

            Then("도메인 페이징만 위임하고 정렬 병합은 하지 않는다") {
                verify(exactly = 1) { recruitmentDomainService.searchOpenRecruitments("축구", pageable) }
            }
        }
    }

    Given("검색 결과가 0건일 때") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val useCase = SearchRecruitmentsForCatalogUseCase(recruitmentDomainService)
        val pageable = PageRequest.of(0, 20)
        every { recruitmentDomainService.searchOpenRecruitments(null, pageable) } returns PageImpl(emptyList(), pageable, 0L)

        When("execute(keyword=null, page=0, size=20)를 호출하면") {
            val result = useCase.execute(keyword = null, page = 0, size = 20)

            Then("빈 목록을 정상 반환한다") {
                result.shouldBeEmpty()
            }
        }
    }
})
