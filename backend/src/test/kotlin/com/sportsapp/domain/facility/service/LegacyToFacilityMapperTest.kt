package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.dto.LegacyRow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class LegacyToFacilityMapperTest : BehaviorSpec({

    fun buildRow(
        legacyId: String = "MAPSERVICE-001",
        ycode: String = "37.5",
        xcode: String = "127.0",
        extraFields: Map<String, String> = emptyMap(),
    ) = LegacyRow(
        legacyId = legacyId,
        name = "테스트 시설",
        gu = "강남구",
        type = "수영장",
        address = "서울시 강남구",
        ycode = ycode,
        xcode = xcode,
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        extraFields = extraFields,
    )

    Given("정상적인 레거시 행이 주어졌을 때") {
        val row = buildRow(legacyId = "MAPSERVICE-001", ycode = "37.5", xcode = "127.0")

        When("map을 호출하면") {
            val result = LegacyToFacilityMapper.map(row)

            Then("[U-01] legacyId가 code로 매핑된다") {
                result.shouldNotBeNull()
                result.code shouldBe "MAPSERVICE-001"
            }

            Then("[U-01] ycode가 lat으로, xcode가 lng으로 변환된다") {
                result.shouldNotBeNull()
                result.lat shouldBe 37.5
                result.lng shouldBe 127.0
            }
        }
    }

    Given("ycode가 숫자로 파싱 불가능한 레거시 행이 주어졌을 때") {
        val row = buildRow(ycode = "INVALID_COORD", xcode = "127.0")

        When("map을 호출하면") {
            val result = LegacyToFacilityMapper.map(row)

            Then("[U-02] null을 반환하고 건너뛴다") {
                result.shouldBeNull()
            }
        }
    }

    Given("xcode가 숫자로 파싱 불가능한 레거시 행이 주어졌을 때") {
        val row = buildRow(ycode = "37.5", xcode = "NOT_A_NUMBER")

        When("map을 호출하면") {
            val result = LegacyToFacilityMapper.map(row)

            Then("[U-02] null을 반환하고 건너뛴다") {
                result.shouldBeNull()
            }
        }
    }

    Given("미정의 필드가 extraFields에 담긴 레거시 행이 주어졌을 때") {
        val extraFields = mapOf("capacity" to "50", "custom_field" to "value123")
        val row = buildRow(extraFields = extraFields)

        When("map을 호출하면") {
            val result = LegacyToFacilityMapper.map(row)

            Then("[U-03] extraFields가 meta 맵으로 fallback 적재된다") {
                result.shouldNotBeNull()
                result.meta shouldContainKey "capacity"
                result.meta["capacity"] shouldBe "50"
                result.meta shouldContainKey "custom_field"
                result.meta["custom_field"] shouldBe "value123"
            }
        }
    }
})
