package com.sportsapp.domain.facility.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FacilityRegionTest : BehaviorSpec({

    Given("FacilityRegion.UNSPECIFIED 상수") {
        When("필드를 조회하면") {
            val region = FacilityRegion.UNSPECIFIED

            Then("시도코드는 00이다") {
                region.sidoCode shouldBe "00"
            }

            Then("시군구코드는 00000이다") {
                region.sigunguCode shouldBe "00000"
            }

            Then("시도명·시군구명은 미지정이다") {
                region.sidoName shouldBe "미지정"
                region.sigunguName shouldBe "미지정"
            }
        }

        When("isUnspecified를 호출하면") {
            Then("true를 반환한다") {
                FacilityRegion.UNSPECIFIED.isUnspecified() shouldBe true
            }
        }
    }

    Given("of 팩토리로 생성한 지역") {
        When("서울 종로구 코드로 생성하면") {
            val region = FacilityRegion.of(
                sidoCode = "11",
                sidoName = "서울특별시",
                sigunguCode = "11110",
                sigunguName = "종로구",
            )

            Then("전달된 코드·명칭을 그대로 보유한다") {
                region.sidoCode shouldBe "11"
                region.sidoName shouldBe "서울특별시"
                region.sigunguCode shouldBe "11110"
                region.sigunguName shouldBe "종로구"
            }

            Then("isUnspecified는 false를 반환한다") {
                region.isUnspecified() shouldBe false
            }
        }
    }
})
