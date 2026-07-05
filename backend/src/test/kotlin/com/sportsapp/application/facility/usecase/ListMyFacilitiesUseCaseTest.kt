package com.sportsapp.application.facility.usecase

import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.service.FacilityOwnerDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.geo.Point

class ListMyFacilitiesUseCaseTest : BehaviorSpec({

    val facilityOwnerDomainService = mockk<FacilityOwnerDomainService>()
    val useCase = ListMyFacilitiesUseCase(facilityOwnerDomainService)

    Given("ownerUserId=1L의 시설 2건이 존재할 때") {
        val facilities = listOf(
            Facility(
                id = "f-001", code = "C-001", name = "시설1", gu = "강남구", type = "수영장",
                address = "주소1", location = Point(127.0, 37.5),
                parking = true, tel = "02-1111-1111", homePage = "", eduYn = false,
                meta = emptyMap(), ownerUserId = 1L,
                sidoCode = null, sidoName = null, sigunguCode = null, sigunguName = null,
            ),
            Facility(
                id = "f-002", code = "C-002", name = "시설2", gu = "서초구", type = "헬스장",
                address = "주소2", location = Point(127.1, 37.6),
                parking = false, tel = "02-2222-2222", homePage = "", eduYn = false,
                meta = emptyMap(), ownerUserId = 1L,
                sidoCode = null, sidoName = null, sigunguCode = null, sigunguName = null,
            ),
        )
        val pageable = PageRequest.of(0, 20)
        every { facilityOwnerDomainService.listByOwner(1L, pageable) } returns PageImpl(facilities, pageable, 2)

        When("[U-01] execute를 호출하면") {
            val result = useCase.execute(ownerUserId = 1L, page = 0, size = 20)

            Then("내 소유 시설 2건이 Page로 반환된다") {
                result.totalElements shouldBe 2
                result.content[0].id shouldBe "f-001"
                result.content[1].id shouldBe "f-002"
            }
        }
    }

    Given("ownerUserId=2L의 시설이 없을 때") {
        val pageable = PageRequest.of(0, 20)
        every { facilityOwnerDomainService.listByOwner(2L, pageable) } returns PageImpl(emptyList(), pageable, 0)

        When("[U-02] execute를 호출하면") {
            val result = useCase.execute(ownerUserId = 2L, page = 0, size = 20)

            Then("빈 Page가 반환된다") {
                result.totalElements shouldBe 0
                result.isEmpty shouldBe true
            }
        }
    }
})
