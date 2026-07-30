package com.sportsapp.infrastructure.facility.region

import com.sportsapp.BaseJpaIntegrationTest
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class RegionResolveGatewayImplTest(
    @Autowired private val regionResolveGatewayImpl: RegionResolveGatewayImpl,
) : BaseJpaIntegrationTest() {

    init {
        Given("정식 시도명을 포함한 주소가 주어진다") {
            val address = "부산광역시 해운대구 재송동 111"

            When("resolve를 호출하면") {
                val region = regionResolveGatewayImpl.resolve(address, sidoHint = null)

                Then("부산 시도코드와 해운대구 시군구코드로 해석된다") {
                    region.sidoCode shouldBe "26"
                    region.sidoName shouldBe "부산광역시"
                    region.sigunguCode shouldBe "26350"
                    region.sigunguName shouldBe "해운대구"
                }
            }
        }

        Given("약식 시도명을 포함한 주소가 주어진다") {
            val address = "부산 해운대구 우동 222"

            When("resolve를 호출하면") {
                val region = regionResolveGatewayImpl.resolve(address, sidoHint = null)

                Then("정규화되어 동일한 시도·시군구코드로 해석된다") {
                    region.sidoCode shouldBe "26"
                    region.sigunguCode shouldBe "26350"
                }
            }
        }

        Given("매핑 가능한 시도·시군구 토큰이 없는 주소가 주어진다") {
            val address = "존재하지않는 우주정거장 123"

            When("resolve를 호출하면") {
                val region = regionResolveGatewayImpl.resolve(address, sidoHint = null)

                Then("예외 없이 UNSPECIFIED가 반환된다") {
                    region.isUnspecified() shouldBe true
                }
            }
        }

        Given("빈 주소가 주어진다") {
            val address = ""

            When("resolve를 호출하면") {
                val region = regionResolveGatewayImpl.resolve(address, sidoHint = null)

                Then("UNSPECIFIED가 반환된다") {
                    region.isUnspecified() shouldBe true
                }
            }
        }

        Given("주소는 서울이지만 sidoHint로 부산이 주어진다") {
            val address = "서울특별시 중구 어딘가 1"

            When("resolve를 호출하면") {
                val region = regionResolveGatewayImpl.resolve(address, sidoHint = "부산")

                Then("주소 파싱 대신 sidoHint가 우선 적용되어 부산 중구로 해석된다") {
                    region.sidoCode shouldBe "26"
                    region.sidoName shouldBe "부산광역시"
                    region.sigunguCode shouldBe "26110"
                    region.sigunguName shouldBe "중구"
                }
            }
        }

        Given("regions에 존재하지 않는 시도·시군구 조합 주소가 주어진다") {
            val address = "부산광역시 없는구 999"

            When("resolve를 호출하면") {
                val region = regionResolveGatewayImpl.resolve(address, sidoHint = null)

                Then("조회 결과가 없으므로 UNSPECIFIED가 반환된다") {
                    region.isUnspecified() shouldBe true
                }
            }
        }
    }
}
