package com.sportsapp.domain.facility

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.data.geo.Point

private fun buildFacility(ownerUserId: Long? = 1L): Facility = Facility(
    id = "fac-001",
    code = "GN-001",
    name = "테스트 시설",
    gu = "강남구",
    type = "수영장",
    address = "서울시 강남구",
    location = Point(127.0, 37.5),
    parking = true,
    tel = "02-0000-0000",
    homePage = "",
    eduYn = false,
    meta = emptyMap(),
    ownerUserId = ownerUserId,
)

class FacilityOwnershipTest : BehaviorSpec({

    Given("ownerUserId=1인 시설") {
        val facility = buildFacility(ownerUserId = 1L)

        When("[U-01] requireOwnedBy(2L) 호출 시") {
            Then("[U-01] FacilityNotOwnedByException이 발생한다") {
                shouldThrow<FacilityNotOwnedByException> {
                    facility.requireOwnedBy(2L)
                }
            }
        }

        When("[U-01] requireOwnedBy(1L) 호출 시") {
            Then("[U-01] 예외 없이 통과한다") {
                facility.requireOwnedBy(1L)
            }
        }
    }

    Given("name=기존명, address=기존주소인 시설") {
        val facility = buildFacility()

        When("[U-01] updateInfo에 name만 변경") {
            val updated = facility.updateInfo(name = "새 이름")
            Then("[U-01] name만 변경되고 나머지 필드는 유지된다") {
                updated.name shouldBe "새 이름"
                updated.address shouldBe facility.address
                updated.meta shouldBe facility.meta
            }
        }

        When("[U-01] updateInfo에 null만 전달") {
            val updated = facility.updateInfo()
            Then("[U-01] 아무 필드도 변경되지 않는다") {
                updated.name shouldBe facility.name
                updated.address shouldBe facility.address
            }
        }

        When("[U-01] updateInfo에 basePrice=-1 전달 시") {
            Then("[U-01] IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    facility.updateInfo(basePrice = -1L)
                }
            }
        }

        When("[U-01] updateInfo에 basePrice=0 전달 시") {
            Then("[U-01] IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    facility.updateInfo(basePrice = 0L)
                }
            }
        }

        When("[U-01] updateInfo에 operatingHours 전달") {
            val updated = facility.updateInfo(operatingHours = "09:00-22:00")
            Then("[U-01] meta에 operating_hours가 저장된다") {
                updated.meta["operating_hours"] shouldBe "09:00-22:00"
            }
        }

        When("[U-01] updateInfo에 basePrice=5000 전달") {
            val updated = facility.updateInfo(basePrice = 5000L)
            Then("[U-01] meta에 base_price가 저장된다") {
                updated.meta["base_price"] shouldBe "5000"
            }
        }
    }
})
