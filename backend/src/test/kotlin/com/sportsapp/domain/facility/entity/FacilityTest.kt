package com.sportsapp.domain.facility.entity

import com.sportsapp.domain.facility.exception.InvalidFacilityException
import com.sportsapp.domain.facility.vo.FacilityAttributes
import com.sportsapp.domain.facility.vo.FacilityRegion
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe

class FacilityTest : BehaviorSpec({

    fun buildAttributes(
        code: String = "GN-001",
        meta: Map<String, String> = emptyMap(),
        region: FacilityRegion = FacilityRegion.UNSPECIFIED,
    ) = FacilityAttributes(
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
        region = region,
    )

    Given("Facility.create 호출 시") {
        When("code가 빈 문자열이면") {
            Then("InvalidFacilityException을 던진다") {
                shouldThrow<InvalidFacilityException> {
                    Facility.create(buildAttributes(code = ""))
                }
            }
        }

        When("code가 공백 문자열이면") {
            Then("InvalidFacilityException을 던진다") {
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
            Then("ownerUserId가 해당 값으로 설정된다") {
                facility.ownerUserId shouldBe 42L
            }
        }
    }

    Given("이미 ownerUserId가 설정된 Facility") {
        val facility = Facility.create(buildAttributes())
        facility.assignOwner(42L)

        When("assignOwner를 재호출하면") {
            Then("IllegalStateException이 발생한다") {
                shouldThrow<IllegalStateException> {
                    facility.assignOwner(99L)
                }
            }
        }
    }

    Given("ownerUserId가 설정된 Facility") {
        val facility = Facility.create(buildAttributes())
        facility.assignOwner(42L)

        When("소유자 userId로 isOwnedBy를 호출하면") {
            Then("true를 반환한다") {
                facility.isOwnedBy(42L) shouldBe true
            }
        }

        When("다른 userId로 isOwnedBy를 호출하면") {
            Then("false를 반환한다") {
                facility.isOwnedBy(99L) shouldBe false
            }
        }
    }

    Given("ownerUserId가 없는 Facility") {
        val facility = Facility.create(buildAttributes())

        When("어떤 userId로 isOwnedBy를 호출해도") {
            Then("false를 반환한다") {
                facility.isOwnedBy(42L) shouldBe false
            }
        }
    }

    Given("기존 meta에 키가 있는 Facility") {
        val facility = Facility.create(
            buildAttributes(meta = mapOf("capacity" to "50", "lane" to "8"))
        )

        When("updateMeta로 기존 키와 신규 키를 포함한 패치를 적용하면") {
            val updated = facility.updateMeta(mapOf("capacity" to "100", "fee" to "5000"))
            Then("기존 capacity 키는 덮어쓰이고 lane 키는 보존되며 fee 키가 추가된다") {
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

    Given("region이 지정된 attributes로 Facility.create를 호출하면") {
        val region = FacilityRegion.of(
            sidoCode = "26",
            sidoName = "부산광역시",
            sigunguCode = "26410",
            sigunguName = "해운대구",
        )
        val facility = Facility.create(buildAttributes(region = region))

        Then("attributes.region의 4필드가 시설에 반영된다") {
            facility.sidoCode shouldBe "26"
            facility.sidoName shouldBe "부산광역시"
            facility.sigunguCode shouldBe "26410"
            facility.sigunguName shouldBe "해운대구"
        }

        Then("gu는 attributes.gu 값이 그대로 보존된다") {
            facility.gu shouldBe "강남구"
        }
    }

    Given("region 정보 없이 생성된(UNSPECIFIED) Facility") {
        val facility = Facility.create(buildAttributes())

        When("assignRegion으로 해석된 region을 갱신하면") {
            val resolved = FacilityRegion.of(
                sidoCode = "11",
                sidoName = "서울특별시",
                sigunguCode = "11680",
                sigunguName = "강남구",
            )
            val updated = facility.assignRegion(resolved)

            Then("region 4필드가 갱신된 새 Facility가 반환된다") {
                updated.sidoCode shouldBe "11"
                updated.sidoName shouldBe "서울특별시"
                updated.sigunguCode shouldBe "11680"
                updated.sigunguName shouldBe "강남구"
            }

            Then("원본 Facility의 region은 변경되지 않는다") {
                facility.sidoCode shouldBe FacilityRegion.UNSPECIFIED.sidoCode
            }
        }
    }
})
