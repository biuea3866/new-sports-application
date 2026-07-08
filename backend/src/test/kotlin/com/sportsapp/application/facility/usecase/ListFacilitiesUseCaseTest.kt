package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.FacilityCriteria
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.service.FacilityDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.geo.Point

class ListFacilitiesUseCaseTest : BehaviorSpec({

    val facilityDomainService = mockk<FacilityDomainService>()
    val listFacilitiesUseCase = ListFacilitiesUseCase(facilityDomainService)

    fun buildFacility(id: String, gu: String, type: String): Facility = Facility(
        id = id,
        code = "CODE-$id",
        name = "시설 $id",
        gu = gu,
        type = type,
        address = "서울시 $gu",
        location = Point(127.0, 37.5),
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        meta = emptyMap(),
        sidoCode = null, sidoName = null, sigunguCode = null, sigunguName = null,
    )

    Given("유효한 gu 필터가 주어졌을 때") {
        val facility = buildFacility("1", "강남구", "수영장")
        val expectedPage = PageImpl(listOf(facility))
        every { facilityDomainService.list(null, null, "강남구", null, any()) } returns expectedPage

        When("FacilityCriteria에 gu만 설정하면") {
            val criteria = FacilityCriteria(sidoCode = null, sigunguCode = null, gu = "강남구", type = null, page = 0, size = 50)
            val result = listFacilitiesUseCase.execute(criteria)

            Then("gu 필터만 적용되어 DomainService가 호출된다") {
                result.totalElements shouldBe 1
                result.content[0].gu shouldBe "강남구"
                verify { facilityDomainService.list(null, null, "강남구", null, any()) }
            }
        }
    }

    Given("빈 문자열 gu가 주어졌을 때") {
        val expectedPage = PageImpl(emptyList<Facility>())
        every { facilityDomainService.list(null, null, null, null, any()) } returns expectedPage

        When("blank gu는 null로 처리되어 전체 조회가 된다") {
            val criteria = FacilityCriteria(sidoCode = null, sigunguCode = null, gu = "  ", type = null, page = 0, size = 50)
            val result = listFacilitiesUseCase.execute(criteria)

            Then("null gu로 DomainService가 호출된다") {
                result.totalElements shouldBe 0
                verify { facilityDomainService.list(null, null, null, null, any()) }
            }
        }
    }

    Given("size가 100을 초과하는 조건이 주어졌을 때") {
        val expectedPage = PageImpl(emptyList<Facility>())
        every { facilityDomainService.list(null, null, null, null, any()) } returns expectedPage

        When("size=200으로 요청하면") {
            val criteria = FacilityCriteria(sidoCode = null, sigunguCode = null, gu = null, type = null, page = 0, size = 200)
            listFacilitiesUseCase.execute(criteria)

            Then("Pageable의 size가 100으로 cap된다") {
                verify {
                    facilityDomainService.list(
                        null,
                        null,
                        null,
                        null,
                        PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "name")),
                    )
                }
            }
        }
    }

    Given("sidoCode·sigunguCode 필터가 주어졌을 때") {
        val expectedPage = PageImpl(emptyList<Facility>())
        every { facilityDomainService.list("26", "26410", null, null, any()) } returns expectedPage

        When("FacilityCriteria에 sidoCode·sigunguCode를 설정하면") {
            val criteria = FacilityCriteria(sidoCode = "26", sigunguCode = "26410", gu = null, type = null, page = 0, size = 50)
            listFacilitiesUseCase.execute(criteria)

            Then("sidoCode·sigunguCode가 DomainService에 그대로 전달된다") {
                verify { facilityDomainService.list("26", "26410", null, null, any()) }
            }
        }
    }

    Given("공백 sidoCode·sigunguCode가 주어졌을 때") {
        val expectedPage = PageImpl(emptyList<Facility>())
        every { facilityDomainService.list(null, null, null, null, any()) } returns expectedPage

        When("FacilityCriteria에 공백 sidoCode·sigunguCode를 설정하면") {
            val criteria = FacilityCriteria(sidoCode = "  ", sigunguCode = " ", gu = null, type = null, page = 0, size = 50)
            listFacilitiesUseCase.execute(criteria)

            Then("null sidoCode·sigunguCode로 DomainService가 호출된다") {
                verify { facilityDomainService.list(null, null, null, null, any()) }
            }
        }
    }
})
