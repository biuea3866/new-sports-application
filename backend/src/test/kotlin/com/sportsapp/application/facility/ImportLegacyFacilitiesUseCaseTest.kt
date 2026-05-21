package com.sportsapp.application.facility

import com.sportsapp.domain.facility.BulkImportResult
import com.sportsapp.domain.facility.FacilityDomainService
import com.sportsapp.domain.facility.LegacyRow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

private fun buildLegacyRow(legacyId: String = "LEGACY-001") = LegacyRow(
    legacyId = legacyId,
    name = "테스트 시설",
    gu = "강남구",
    type = "수영장",
    address = "서울시 강남구",
    ycode = "37.5",
    xcode = "127.0",
    parking = true,
    tel = "02-0000-0000",
    homePage = "",
    eduYn = false,
    extraFields = emptyMap(),
)

class ImportLegacyFacilitiesUseCaseTest : BehaviorSpec({

    val facilityDomainService = mockk<FacilityDomainService>()
    val importLegacyFacilitiesUseCase = ImportLegacyFacilitiesUseCase(facilityDomainService)

    Given("dryRun=true 커맨드가 주어졌을 때") {
        val rows = listOf(buildLegacyRow("A-001"), buildLegacyRow("A-002"), buildLegacyRow("A-003"))
        val command = ImportLegacyFacilitiesCommand(rows = rows, dryRun = true)

        When("execute를 호출하면") {
            val result = importLegacyFacilitiesUseCase.execute(command)

            Then("[R-02] dryRun=true가 반환되고 bulkImport는 호출되지 않는다") {
                result.dryRun shouldBe true
                result.insertedCount shouldBe 3
                verify(exactly = 0) { facilityDomainService.bulkImport(any()) }
            }
        }
    }

    Given("dryRun=false이고 3건 삽입·1건 업데이트·1건 스킵 결과가 반환될 때") {
        val rows = (1..5).map { buildLegacyRow("ROW-00$it") }
        val command = ImportLegacyFacilitiesCommand(rows = rows, dryRun = false)
        val bulkResult = BulkImportResult(insertedCount = 3, updatedCount = 1, skippedCount = 1)
        every { facilityDomainService.bulkImport(rows) } returns bulkResult

        When("execute를 호출하면") {
            val result = importLegacyFacilitiesUseCase.execute(command)

            Then("실제 bulkImport 결과가 응답에 반영된다") {
                result.dryRun shouldBe false
                result.insertedCount shouldBe 3
                result.updatedCount shouldBe 1
                result.skippedCount shouldBe 1
            }
        }
    }
})
