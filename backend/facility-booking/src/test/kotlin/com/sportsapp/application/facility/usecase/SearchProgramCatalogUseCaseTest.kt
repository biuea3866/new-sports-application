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
import io.mockk.verify
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

    fun program(
        id: Long,
        name: String = "1:1 PT",
        price: BigDecimal = BigDecimal("50000"),
        facilityId: String = "FAC-01",
    ): Program =
        Program.create(
            facilityId = facilityId,
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
        every { programDomainService.findFacilityNamesBy(listOf("FAC-01", "FAC-01")) } returns mapOf("FAC-01" to "강남 스포츠센터")
        val useCase = SearchProgramCatalogUseCase(programDomainService)

        When("execute를 호출하면") {
            val result = useCase.execute(InternalProgramCatalogQuery(keyword = "PT", page = 0, size = 20))

            Then("계약 필드(sourceId·title·price·createdAt)만 담은 응답을 반환한다") {
                result.map { it.sourceId } shouldBe listOf(1L, 2L)
                result.map { it.title } shouldBe listOf("1:1 PT", "1:1 PT")
            }
        }
    }

    Given("시설 2곳이 같은 이름의 시설상품을 등록한 상황에서") {
        val programDomainService = mockk<ProgramDomainService>()
        val programs = listOf(
            program(id = 1L, facilityId = "FAC-01"),
            program(id = 2L, facilityId = "FAC-02"),
        )
        every { programDomainService.searchForCatalog(null, PageRequest.of(0, 20)) } returns
            PageImpl(programs, PageRequest.of(0, 20), 2)
        every { programDomainService.findFacilityNamesBy(listOf("FAC-01", "FAC-02")) } returns
            mapOf("FAC-01" to "강남 스포츠센터", "FAC-02" to "판교 스포츠센터")
        val useCase = SearchProgramCatalogUseCase(programDomainService)

        When("execute를 호출하면") {
            val result = useCase.execute(InternalProgramCatalogQuery(keyword = null, page = 0, size = 20))

            Then("시설명을 함께 공급해 통합 카탈로그에서 두 항목을 구분할 수 있게 한다") {
                result.map { it.locationName } shouldBe listOf("강남 스포츠센터", "판교 스포츠센터")
            }

            Then("시설명은 facilityId 를 모아 한 번만 배치 조회한다 (N+1 방지)") {
                verify(exactly = 1) { programDomainService.findFacilityNamesBy(listOf("FAC-01", "FAC-02")) }
            }
        }
    }

    Given("참조 시설이 삭제돼 시설명을 찾을 수 없는 시설상품이면") {
        val programDomainService = mockk<ProgramDomainService>()
        every { programDomainService.searchForCatalog(null, PageRequest.of(0, 20)) } returns
            PageImpl(listOf(program(id = 3L, facilityId = "FAC-GONE")), PageRequest.of(0, 20), 1)
        every { programDomainService.findFacilityNamesBy(listOf("FAC-GONE")) } returns emptyMap()
        val useCase = SearchProgramCatalogUseCase(programDomainService)

        When("execute를 호출하면") {
            val result = useCase.execute(InternalProgramCatalogQuery(keyword = null, page = 0, size = 20))

            Then("시설명을 null 로 공급한다 (빈 문자열·유형명 반복 금지)") {
                result.single().locationName shouldBe null
            }
        }
    }

    Given("조회 결과가 0건이면") {
        val programDomainService = mockk<ProgramDomainService>()
        every { programDomainService.searchForCatalog(any(), any()) } returns PageImpl(emptyList(), PageRequest.of(0, 20), 0)
        every { programDomainService.findFacilityNamesBy(emptyList()) } returns emptyMap()
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
