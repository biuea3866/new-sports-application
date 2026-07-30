package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.UpdateMyFacilityCommand
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.exception.FacilityNotFoundException
import com.sportsapp.domain.facility.service.FacilityOwnerDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.geo.Point

class UpdateMyFacilityUseCaseTest : BehaviorSpec({

    val facilityOwnerDomainService = mockk<FacilityOwnerDomainService>()
    val useCase = UpdateMyFacilityUseCase(facilityOwnerDomainService)

    Given("ownerUserId=1L 소유 시설에 meta 패치 command가 주어졌을 때") {
        val command = UpdateMyFacilityCommand(
            facilityId = "f-001",
            ownerUserId = 1L,
            patch = mapOf("key" to "value"),
        )
        val updatedFacility = Facility(
            id = "f-001", code = "C-001", name = "강남 수영장",
            gu = "강남구", type = "수영장", address = "서울시 강남구",
            location = Point(127.0, 37.5),
            parking = true, tel = "02-0000-0000", homePage = "", eduYn = false,
            meta = mapOf("key" to "value"), ownerUserId = 1L,
            sidoCode = null, sidoName = null, sigunguCode = null, sigunguName = null,
        )
        every { facilityOwnerDomainService.updateMetaForOwner("f-001", 1L, mapOf("key" to "value"), null) } returns updatedFacility

        When("[U-01] execute를 호출하면") {
            val result = useCase.execute(command)

            Then("업데이트된 FacilityResponse가 반환된다") {
                result.id shouldBe "f-001"
            }
        }
    }

    Given("ownerUserId=1L 소유 시설에 sido를 함께 담은 command가 주어졌을 때") {
        val command = UpdateMyFacilityCommand(
            facilityId = "f-005",
            ownerUserId = 1L,
            patch = mapOf("key" to "value"),
            sido = "부산",
        )
        val updatedFacility = Facility(
            id = "f-005", code = "C-005", name = "부산 수영장",
            gu = "해운대구", type = "수영장", address = "부산광역시 해운대구",
            location = Point(129.1, 35.1),
            parking = true, tel = "051-0000-0000", homePage = "", eduYn = false,
            meta = mapOf("key" to "value"), ownerUserId = 1L,
            sidoCode = "26", sidoName = "부산광역시", sigunguCode = "26350", sigunguName = "해운대구",
        )
        every { facilityOwnerDomainService.updateMetaForOwner("f-005", 1L, mapOf("key" to "value"), "부산") } returns updatedFacility

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("sido가 DomainService에 그대로 전달되어 재해석된 지역이 반영된다") {
                result.id shouldBe "f-005"
            }
        }
    }

    Given("다른 사용자 소유 시설 ID로 command가 주어졌을 때") {
        val command = UpdateMyFacilityCommand(
            facilityId = "f-999",
            ownerUserId = 1L,
            patch = mapOf("key" to "value"),
        )
        every { facilityOwnerDomainService.updateMetaForOwner("f-999", 1L, any(), any()) } throws FacilityNotFoundException("f-999")

        When("[U-02] execute를 호출하면") {
            Then("FacilityNotFoundException을 던진다") {
                shouldThrow<FacilityNotFoundException> {
                    useCase.execute(command)
                }
            }
        }
    }
})
