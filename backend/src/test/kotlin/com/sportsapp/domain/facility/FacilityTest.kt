package com.sportsapp.domain.facility

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe

class FacilityTest : BehaviorSpec({

    fun buildAttributes(code: String = "GN-001", meta: Map<String, String> = emptyMap()) =
        FacilityAttributes(
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
            meta = meta,
        )

    Given("Facility.create 호출 시") {
        When("code가 빈 문자열이면") {
            Then("[U-02] InvalidFacilityException을 던진다") {
                shouldThrow<InvalidFacilityException> {
                    Facility.create(buildAttributes(code = ""))
                }
            }
        }

        When("code가 공백 문자열이면") {
            Then("[U-02] InvalidFacilityException을 던진다") {
                shouldThrow<InvalidFacilityException> {
                    Facility.create(buildAttributes(code = "   "))
                }
            }
        }

        When("유효한 code가 주어지면") {
            Then("Facility 인스턴스가 생성된다") {
                val facility = Facility.create(buildAttributes(code = "GN-001"))
                facility.code shouldBe "GN-001"
                facility.gu shouldBe "강남구"
            }
        }
    }

    Given("ownerUserId가 없는 Facility") {
        val facility = Facility.create(buildAttributes())

        When("assignOwner를 호출하면") {
            facility.assignOwner(42L)
            Then("[U-01] ownerUserId가 해당 값으로 설정된다") {
                facility.ownerUserId shouldBe 42L
            }
        }
    }

    Given("이미 ownerUserId가 설정된 Facility") {
        val facility = Facility.create(buildAttributes())
        facility.assignOwner(42L)

        When("assignOwner를 재호출하면") {
            Then("[U-02] IllegalStateException이 발생한다") {
                shouldThrow<IllegalStateException> {
                    facility.assignOwner(99L)
                }
            }
        }
    }

    Given("기존 meta에 키가 있는 Facility") {
        val facility = Facility.create(
            buildAttributes(meta = mapOf("capacity" to "50", "lane" to "8"))
        )

        When("updateMeta로 기존 키와 신규 키를 포함한 패치를 적용하면") {
            val updated = facility.updateMeta(mapOf("capacity" to "100", "fee" to "5000"))
            Then("[U-01] 기존 capacity 키는 덮어쓰이고 lane 키는 보존되며 fee 키가 추가된다") {
                updated.meta["capacity"] shouldBe "100"
                updated.meta shouldContainKey "lane"
                updated.meta["lane"] shouldBe "8"
                updated.meta shouldContainKey "fee"
                updated.meta["fee"] shouldBe "5000"
            }

            Then("원본 Facility의 meta는 변경되지 않는다") {
                facility.meta["capacity"] shouldBe "50"
                facility.meta shouldNotContainKey "fee"
            }
        }
    }
})
