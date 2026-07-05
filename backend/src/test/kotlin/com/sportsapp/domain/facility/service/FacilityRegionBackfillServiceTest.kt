package com.sportsapp.domain.facility.service

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.exception.FacilityRegionBackfillInProgressException
import com.sportsapp.domain.facility.gateway.RegionResolveGateway
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.vo.FacilityAttributes
import com.sportsapp.domain.facility.vo.FacilityRegion
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

private fun buildAttributes(code: String, address: String, region: FacilityRegion = FacilityRegion.UNSPECIFIED) =
    FacilityAttributes(
        code = code,
        name = "시설 $code",
        gu = "임의구",
        type = "수영장",
        address = address,
        lat = 37.5,
        lng = 127.0,
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        meta = emptyMap(),
        region = region,
    )

class FacilityRegionBackfillServiceTest : BehaviorSpec({

    Given("해석 가능한 주소를 가진 시설 문서 2건이 있는 상태") {
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val distributedLock = mockk<DistributedLock>()
        val service = FacilityRegionBackfillService(facilityRepository, regionResolveGateway, distributedLock)

        val seoulFacility = Facility.create(buildAttributes("F-SEOUL", "서울시 강남구"))
        val busanFacility = Facility.create(buildAttributes("F-BUSAN", "부산시 해운대구"))
        val page = PageImpl(listOf(seoulFacility, busanFacility), PageRequest.of(0, 10), 2)
        val seoulRegion = FacilityRegion.of("11", "서울특별시", "11680", "강남구")
        val busanRegion = FacilityRegion.of("26", "부산광역시", "26410", "해운대구")

        every { distributedLock.tryLock(any(), any(), any()) } returns true
        every { distributedLock.unlock(any(), any()) } returns true
        every { facilityRepository.findAllForBackfill(any()) } returns page
        every { regionResolveGateway.resolve("서울시 강남구", null) } returns seoulRegion
        every { regionResolveGateway.resolve("부산시 해운대구", null) } returns busanRegion
        every { facilityRepository.save(any()) } answers { firstArg() }

        When("backfill을 실행하면") {
            val result = service.backfill(pageSize = 10)

            Then("각 문서가 해석된 시도·시군구 코드로 저장되고 updated 카운트에 반영된다") {
                result.updated shouldBe 2
                result.unspecified shouldBe 0
                verify { facilityRepository.save(match { it.sidoCode == "11" && it.sigunguCode == "11680" }) }
                verify { facilityRepository.save(match { it.sidoCode == "26" && it.sigunguCode == "26410" }) }
            }

            Then("락을 획득하고 완료 후 해제한다") {
                verify(exactly = 1) { distributedLock.tryLock("lock:facility:region-backfill", any(), any()) }
                verify(exactly = 1) { distributedLock.unlock("lock:facility:region-backfill", any()) }
            }
        }
    }

    Given("주소 파싱에 실패해 UNSPECIFIED로만 해석되는 문서가 있는 상태") {
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val distributedLock = mockk<DistributedLock>()
        val service = FacilityRegionBackfillService(facilityRepository, regionResolveGateway, distributedLock)

        val unresolvableFacility = Facility.create(buildAttributes("F-UNKNOWN", "알수없는주소"))
        val page = PageImpl(listOf(unresolvableFacility), PageRequest.of(0, 10), 1)

        every { distributedLock.tryLock(any(), any(), any()) } returns true
        every { distributedLock.unlock(any(), any()) } returns true
        every { facilityRepository.findAllForBackfill(any()) } returns page
        every { regionResolveGateway.resolve("알수없는주소", null) } returns FacilityRegion.UNSPECIFIED
        every { facilityRepository.save(any()) } answers { firstArg() }

        When("backfill을 실행하면") {
            val result = service.backfill(pageSize = 10)

            Then("문서가 삭제·스킵되지 않고 UNSPECIFIED로 보존되며 unspecified 카운트에 반영된다") {
                result.updated shouldBe 1
                result.unspecified shouldBe 1
                verify(exactly = 1) { facilityRepository.save(match { it.sidoCode == FacilityRegion.UNSPECIFIED_SIDO }) }
            }
        }
    }

    Given("이미 해석된 문서에 동일한 데이터로 백필을 재실행하는 상태") {
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val distributedLock = mockk<DistributedLock>()
        val service = FacilityRegionBackfillService(facilityRepository, regionResolveGateway, distributedLock)

        val seoulRegion = FacilityRegion.of("11", "서울특별시", "11680", "강남구")
        val alreadyResolved = Facility.create(buildAttributes("F-DONE", "서울시 강남구", seoulRegion))
        val page = PageImpl(listOf(alreadyResolved), PageRequest.of(0, 10), 1)

        every { distributedLock.tryLock(any(), any(), any()) } returns true
        every { distributedLock.unlock(any(), any()) } returns true
        every { facilityRepository.findAllForBackfill(any()) } returns page
        every { regionResolveGateway.resolve("서울시 강남구", null) } returns seoulRegion
        every { facilityRepository.save(any()) } answers { firstArg() }

        When("동일한 상태에서 backfill을 두 번 실행하면") {
            val firstRun = service.backfill(pageSize = 10)
            val secondRun = service.backfill(pageSize = 10)

            Then("두 실행 결과가 동일하다(멱등)") {
                firstRun.updated shouldBe secondRun.updated
                firstRun.unspecified shouldBe secondRun.unspecified
                secondRun.unspecified shouldBe 0
                verify(exactly = 2) { facilityRepository.save(match { it.sidoCode == "11" }) }
            }
        }
    }

    Given("이미 다른 백필이 진행 중이어서 락 획득에 실패하는 상태") {
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val distributedLock = mockk<DistributedLock>()
        val service = FacilityRegionBackfillService(facilityRepository, regionResolveGateway, distributedLock)

        every { distributedLock.tryLock(any(), any(), any()) } returns false

        When("backfill을 실행하면") {
            Then("FacilityRegionBackfillInProgressException을 던지고 즉시 중단한다") {
                shouldThrow<FacilityRegionBackfillInProgressException> {
                    service.backfill(pageSize = 10)
                }
                verify(exactly = 0) { facilityRepository.findAllForBackfill(any()) }
                verify(exactly = 0) { distributedLock.unlock(any(), any()) }
            }
        }
    }

    Given("전체 문서가 두 페이지에 걸쳐 존재하는 상태") {
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val distributedLock = mockk<DistributedLock>()
        val service = FacilityRegionBackfillService(facilityRepository, regionResolveGateway, distributedLock)

        val firstPageFacility = Facility.create(buildAttributes("F-PAGE1", "서울시 강남구"))
        val secondPageFacility = Facility.create(buildAttributes("F-PAGE2", "부산시 해운대구"))
        val firstPageable = PageRequest.of(0, 1)
        val secondPageable = PageRequest.of(1, 1)
        val firstPage = PageImpl(listOf(firstPageFacility), firstPageable, 2)
        val secondPage = PageImpl(listOf(secondPageFacility), secondPageable, 2)

        every { distributedLock.tryLock(any(), any(), any()) } returns true
        every { distributedLock.unlock(any(), any()) } returns true
        every { facilityRepository.findAllForBackfill(firstPageable) } returns firstPage
        every { facilityRepository.findAllForBackfill(secondPageable) } returns secondPage
        every { regionResolveGateway.resolve(any(), null) } returns FacilityRegion.of("11", "서울특별시", "11680", "강남구")
        every { facilityRepository.save(any()) } answers { firstArg() }

        When("pageSize=1로 backfill을 실행하면") {
            val result = service.backfill(pageSize = 1)

            Then("두 페이지 모두 순회해 문서별로 저장하고 합산된 결과를 반환한다") {
                result.updated shouldBe 2
                verify(exactly = 1) { facilityRepository.findAllForBackfill(firstPageable) }
                verify(exactly = 1) { facilityRepository.findAllForBackfill(secondPageable) }
                verify(exactly = 2) { facilityRepository.save(any()) }
            }
        }
    }
})
