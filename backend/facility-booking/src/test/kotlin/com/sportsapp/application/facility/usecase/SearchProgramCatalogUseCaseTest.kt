package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.InternalProgramCatalogQuery
import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.facility.entity.Program
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.service.ProgramDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

/** 순수 단위 테스트에서 JPA 생성 전략·auditing 이 채울 필드를 강제 주입한다(GoodsDomainServiceTest와 동일 패턴). */
private fun <T : Any> forceField(entity: T, declaringClass: Class<*>, fieldName: String, value: Any) {
    val field = declaringClass.getDeclaredField(fieldName)
    field.isAccessible = true
    field.set(entity, value)
}

class SearchProgramCatalogUseCaseTest : BehaviorSpec({

    fun program(id: Long, name: String = "1:1 PT", price: BigDecimal = BigDecimal("50000")): Program =
        Program.create(
            facilityId = "FAC-01",
            ownerUserId = 1L,
            name = name,
            description = null,
            price = price,
            capacity = 1,
            durationMinutes = 60,
        ).also {
            forceField(it, Program::class.java, "id", id)
            forceField(it, JpaAuditingBase::class.java, "createdAt", ZonedDateTime.now())
        }

    Given("키워드·페이지 조건으로 시설상품 목록을 조회하면") {
        val programDomainService = mockk<ProgramDomainService>()
        val programs = listOf(program(id = 1L), program(id = 2L))
        val page: Page<Program> = PageImpl(programs, PageRequest.of(0, 20), 2)
        every { programDomainService.searchForCatalog("PT", PageRequest.of(0, 20)) } returns page
        val useCase = SearchProgramCatalogUseCase(programDomainService)

        When("execute를 호출하면") {
            val result = useCase.execute(InternalProgramCatalogQuery(keyword = "PT", page = 0, size = 20))

            Then("계약 필드(sourceId·title·price·createdAt)만 담은 응답을 반환한다") {
                result.map { it.sourceId } shouldBe listOf(1L, 2L)
                result.map { it.title } shouldBe listOf("1:1 PT", "1:1 PT")
            }
        }
    }

    Given("조회 결과가 0건이면") {
        val programDomainService = mockk<ProgramDomainService>()
        every { programDomainService.searchForCatalog(any(), any()) } returns PageImpl(emptyList(), PageRequest.of(0, 20), 0)
        val useCase = SearchProgramCatalogUseCase(programDomainService)

        When("execute를 호출하면") {
            val result = useCase.execute(InternalProgramCatalogQuery(keyword = null, page = 0, size = 20))

            Then("빈 목록을 반환한다") {
                result shouldBe emptyList()
            }
        }
    }

    Given("이 UseCase의 의존 구성을") {
        Then("MongoDB 소유 저장소(FacilityRepository)를 의존하지 않는다") {
            val constructorParameterTypes = SearchProgramCatalogUseCase::class.java.declaredConstructors
                .single()
                .parameterTypes
            constructorParameterTypes.contains(FacilityRepository::class.java) shouldBe false
        }
    }
})
