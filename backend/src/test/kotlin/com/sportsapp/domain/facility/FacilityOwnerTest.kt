package com.sportsapp.domain.facility

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FacilityOwnerTest : BehaviorSpec({

    fun buildAttributes(code: String = "GN-001") = FacilityAttributes(
        code = code,
        name = "테스트 시설",
        gu = "강남구",
        type = "수영장",
        address = "서울시 강남구",
        lat = 37.5,
        lng = 127.0,
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        meta = emptyMap(),
    )

    Given("owner가 없는 Facility") {
        val facility = Facility.create(buildAttributes())

        When("assignOwner(userId)를 호출하면") {
            facility.assignOwner(42L)
            Then("[U-01] ownerUserId가 해당 값으로 설정된다") {
                facility.ownerUserId shouldBe 42L
            }
        }
    }

    Given("이미 owner가 있는 Facility") {
        val facility = Facility.create(buildAttributes())
        facility.assignOwner(42L)

        When("다시 assignOwner를 호출하면") {
            Then("[U-02] IllegalStateException이 발생한다") {
                shouldThrow<IllegalStateException> {
                    facility.assignOwner(99L)
                }
            }
        }
    }

    Given("owner가 있는 Facility") {
        val facility = Facility.create(buildAttributes())
        facility.assignOwner(42L)

        When("updateMeta(patch)를 호출하면") {
            val updated = facility.updateMeta(mapOf("note" to "renovated"))
            Then("[U-03] 새 Facility에도 ownerUserId가 보존된다") {
                updated.ownerUserId shouldBe 42L
            }
        }
    }
})
